from mlserver import MLModel, types
from mlserver.utils import get_model_uri
from mlserver.errors import InferenceError
import json
import random

# Um runtime é definido por uma classe que é filha da classe MLModel,
# que faz parte da API do MLServer
class FakeRuntime(MLModel):
    # o método load é responsável por carregar o modelo do disco
    # conforme especificado na URI
    async def load(self) -> bool:
        # primeiro vamos obter a localização do modelo
        # o código a seguir recupera a URI especificada
        # pelo usuário no arquivo model-settings.json
        model_uri = await get_model_uri(self._settings)

        # Vamos ler os parâmetros diretamente do model-settings.json
        # Ao invés do arquivo JSON.
        # A variável "extra" vai receber os parâmetros que quisermos
        # colocar ao configurar o modelo. No caso, vamos usar para
        # configurar o nome da saída e as classes para a "predição"
        extra = self._settings.parameters.extra
        self.output_name = extra['output_name']
        self.classes = extra['classes']

        # agora vamos carregar esse modelo. Neste exemplo fake,
        # estamos esperando que venha um json
        with open(model_uri) as model_file:
            self._model = json.load(model_file)

        # Vamos alimentar o componente aleatório com a semente lida
        random.seed = self._model['seed']

        # se tudo der certo, temos que dizer ao MLServer que
        # o modelo está pronto para receber pedidos de 
        # inferência
        self.ready = True
        return self.ready

    # o método predict é chamado a cada pedido de inferência
    # o parâmetro payload contém o conteúdo da requisição, seguindo o formato
    # do protocolo V2 para inferência: https://kserve.github.io/website/modelserving/inference_api/
    async def predict(self, payload: types.InferenceRequest) -> types.InferenceResponse:
        # Nosso modelo espera que apenas um input seja fornecido. Caso
        # seja fornecido 0 ou mais do que um, vamos retornar um erro para o cliente
        if len(payload.inputs) != 1:
            raise InferenceError(f"FakeRuntime supports a single input tensor ({len(payload.inputs)} were received)")
        
        # Agora vamos ler o input (só deve existir um, depois da checagem acima)
        singleInput = payload.inputs[0]
        
        # A variável classes irá conter as classes determinadas pelo nosso modelo
        classes = []

        # O loop a seguir lê a entrada. Estamos esperando uma lista de strings
        for txt in singleInput.data: 

            # Aqui é a parte "fake" do nosso modelo. Na verdade não estamos
            # fazendo nada, apenas sorteando uma entre as possíveis classes
            # especificadas no model-settings
            # classes.append(random.choice(self._model['classes']))
            classes.append(random.choice(self.classes))

        # Por fim, vamos retornar uma resposta, também seguindo o protocolo
        # de inferência V2: https://kserve.github.io/website/modelserving/inference_api/
        return types.InferenceResponse(
            id=payload.id, # o id da requisição volta para a resposta
            model_name=self.name, # o nome do modelo
            model_version=self.version, # a versão do modelo
            outputs=[types.ResponseOutput( # aqui começa a saída
                # name=self._model['output_name'], # o nome da saída também faz parte do json do modelo
                name=self.output_name, # o nome da saída foi configurado no model-settings
                shape=[len(classes)], # o shape da saída é simples. Um vetor unidimensional, cujo comprimento é o número de classes
                datatype="BYTES", # o tipo da saída será string, que segundo o protocolo V2 deve ser especificado pelo tipo BYTES
                data=classes) # por fim, vamos enviar as classes "previstas" (na verdade, randomicamente selecionadas)
            ]
        )
