# módulos do MLServer
from mlserver import MLModel, types
from mlserver.utils import get_model_uri
from mlserver.errors import InferenceError

# módulos necessários para o BERT
import torch
import torch.nn.functional as F
from transformers import BertTokenizer, BertModel

# módulos gerais
import json
import random

# Classe que encapsula o classificador de sentimentos
class SentimentClassifier(torch.nn.Module):
    def __init__(self, n_classes, bert_model):
        super(SentimentClassifier, self).__init__()
        self.bert = BertModel.from_pretrained(bert_model,
                                              return_dict=False)
        self.drop = torch.nn.Dropout(p=0.3)
        self.out = torch.nn.Linear(self.bert.config.hidden_size, n_classes)

    def forward(self, input_ids, attention_mask):
        _, pooled_output = self.bert(input_ids=input_ids, attention_mask=attention_mask)
        output = self.drop(pooled_output)
        return self.out(output)

# Classe do runtime BERT para o MLServer
class BertRuntime(MLModel):
    async def load(self) -> bool:
        # Vamos obter o caminho do arquivo do modelo
        model_uri = await get_model_uri(self._settings)

        # Parâmetros específicos do BERT, configurados via "extra"
        extra = self._settings.parameters.extra
        self.output_name = extra['OUTPUT_NAME']
        self.bert_model = extra['BERT_MODEL']
        self.class_names = extra['CLASS_NAMES']
        self.max_sequence_len = extra['MAX_SEQUENCE_LEN']

        # Carregando o modelo e configurando o classificador
        self.device = torch.device("cuda:0" if torch.cuda.is_available()
                                    else "cpu")
        self.tokenizer = BertTokenizer.from_pretrained(self.bert_model)

        classifier = SentimentClassifier(len(self.class_names), self.bert_model)
        classifier.load_state_dict(
            torch.load(model_uri, map_location=self.device)
        )
        classifier = classifier.eval()
        self.classifier = classifier.to(self.device)

        # Carregamento bem-sucedido, vamos sinalizar ao MLServer que estamos prontos
        self.ready = True
        return self.ready

    # Método que faz uma predição
    async def predict(self, payload: types.InferenceRequest) -> types.InferenceResponse:
        # Checando se de fato está sendo enviado apenas um input
        if len(payload.inputs) != 1:
            raise InferenceError(f"FakeModel supports a single input tensor ({len(payload.inputs)} were received)")
        
        singleInput = payload.inputs[0]
        
        # Variável que armazenará os resultados das predições
        results = []

        # Para cada texto enviado fazemos o processamento
        for txt in singleInput.data: 
            # Primeiro a tokenização e outros preprocessamentos
            encoded_text = self.tokenizer.encode_plus( txt,
                                                    max_length = self.max_sequence_len,
                                                    add_special_tokens = True,
                                                    return_token_type_ids = False,
                                                    pad_to_max_length = True,
                                                    return_attention_mask = True,
                                                    return_tensors = "pt")
            input_ids = encoded_text["input_ids"].to(self.device)
            attention_mask = encoded_text["attention_mask"].to(self.device)

            with torch.no_grad():
                #Obtendo a predição
                probabilities = F.softmax(self.classifier(input_ids, attention_mask),
                                dim = 1)

            # Selecionando o resultado mais provável
            confidence, predicted_class = torch.max(probabilities, dim = 1)
            predicted_class = predicted_class.cpu().item()
            probabilities = probabilities.flatten().cpu().numpy().tolist()

            # Montando a resposta e adicionando ao array de resultados
            results.append({
                "original_text": txt,
                "predicted_class": self.class_names[predicted_class],
                "confidence": confidence,
                "probabilities": dict(zip(self.class_names, probabilities))
            })

        # Depois que todos os textos de entrada foram processados,
        # vamos montar a resposta para o MLServer
        return types.InferenceResponse(
            id=payload.id,
            model_name=self.name,
            model_version=self.version,
            outputs=[types.ResponseOutput(
                name=self.output_name,
                shape=[len(results)],
                datatype="BYTES",
                data=results)
            ]
        )
