package mba.ufscar.iti.eml;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.ByteString;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import mba.ufscar.iti.eml.grpc.GRPCInferenceServiceGrpc;
import mba.ufscar.iti.eml.grpc.GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingStub;
import mba.ufscar.iti.eml.grpc.MLServer.InferParameter;
import mba.ufscar.iti.eml.grpc.MLServer.InferTensorContents;
import mba.ufscar.iti.eml.grpc.MLServer.ModelInferRequest;
import mba.ufscar.iti.eml.grpc.MLServer.ModelInferRequest.InferInputTensor;
import mba.ufscar.iti.eml.grpc.MLServer.ModelInferResponse;
import mba.ufscar.iti.eml.grpc.MLServer.ModelReadyRequest;
import mba.ufscar.iti.eml.grpc.MLServer.ModelReadyResponse;
import mba.ufscar.iti.eml.grpc.MLServer.ServerLiveRequest;
import mba.ufscar.iti.eml.grpc.MLServer.ServerLiveResponse;
import mba.ufscar.iti.eml.grpc.MLServer.ServerReadyRequest;
import mba.ufscar.iti.eml.grpc.MLServer.ServerReadyResponse;


public class App {
    private static void printBanner() {
        // Vamos gerar um banner para nosso aplicativo.
        // Você pode gerar um também facilmente no endereço
        // https://manytools.org/hacker-tools/ascii-banner/
        System.out.println("""
                    8888888888 .d8888b.                888                   888
                    888       d88P  Y88b               888                   888
                    888       888    888               888                   888
                    8888888   888         8888b.   .d88888  8888b.  .d8888b  888888 888d888 .d88b.
                    888       888            "88b d88" 888     "88b 88K      888    888P"  d88""88b
                    888       888    888 .d888888 888  888 .d888888 "Y8888b. 888    888    888  888
                    888       Y88b  d88P 888  888 Y88b 888 888  888      X88 Y88b.  888    Y88..88P
                    8888888888 "Y8888P"  "Y888888  "Y88888 "Y888888  88888P'  "Y888 888     "Y88P"
                """);
    }

    // Esse é o método principal.
    public static void main(String[] args) throws InterruptedException, IOException, URISyntaxException {
        printBanner();
        System.out.println("Abrindo canal de comunicação com o servidor...");

        // Aqui fazemos a conexão, especificando endereço e porta,
        // No caso é localhost:8081
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8081)
                .usePlaintext()
                .build();

        // Em seguida, obtemos um "stub", que é um objeto que vai se comunicar com o servidor
        // Neste caso, estamos utilizando um "BlockingStub", que é um objeto que bloqueia a
        // execução até que a resposta chegue. Existem outras versões, como a non-blocking,
        // que utiliza "callbacks" para possibilitar um modelo menos travado de execução
        GRPCInferenceServiceBlockingStub MLServer = GRPCInferenceServiceGrpc.newBlockingStub(channel);

        try {
            // O método "realizarCadastro" vai ter a lógica principal do nosso aplicativo
            realizarCadastro(MLServer);
        } catch (IOException | URISyntaxException | InterruptedException e) {
            // Em Java, é boa prática especificar todas as exceções que podem existir
            // durante a execução, e implementar um tratamento adequado
            // No mínimo, deve-se fazer um log do que ocorreu, para depuração
            System.out.println("Ocorreu um erro na execução");
            e.printStackTrace();
        } finally {
            // O canal de comunicação aberto deve ser fechado antes de encerrarmos o processo
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // Aqui temos a lógica do aplicativo
    static void realizarCadastro(GRPCInferenceServiceBlockingStub MLServer)
            throws IOException, URISyntaxException, InterruptedException {
        boolean completed = false;
        // O cadastro será feito em um loop. Cada iteração irá fazer as verificações e fazer
        // a predição de uma categoria de produto.
        // Caso as verificações falhem, o loop retorna do início.
        do {
            try {
                System.out.println("Verificando se o servidor está vivo...");

                // A seguir fazemos nossa primeira requisição ao servidor. Estamos utilizando um
                // serviço que checa se o servidor está vivo. Isso é feito através do serviço
                // chamado "serverLive".
                // Cada serviço tem uma mensagem de entrada e uma de saída, e isso é especificado
                // no arquivo .proto. Veja lá e compare com os objetos Java que foram gerados para utilizar aqui
                // Os objetos Java utilizam o padrão Builder para sua construção ser mais facilitada.
                ServerLiveResponse serverLiveResponse = MLServer.serverLive(ServerLiveRequest.newBuilder().build());
                if (!serverLiveResponse.getLive()) {
                    // Isso não faz muito sentido, pois se o servidor não está vivo ele normalmente não responde
                    // (ocorre timeout). Mas é assim que foi especificado no .proto, e é assim que o protocolo
                    // foi definido. Pode ser que haja situação em que o servidor esteja rodando mas não esteja
                    // apto, e aí ele vai responder.
                    System.out.println("Servidor respondeu que não está vivo!");
                    // Caso o servidor não estja vivo, vamos esperar 10 segundos e tentar novamente depois disso
                    Thread.sleep(10000);
                    continue;
                } else {
                    // Se o servidor estiver vivo, vamos seguir em frente
                    System.out.println("Servidor está vivo!");
                }

                // Podemos acrescentar outras verificações, para saber se o servidor, além de vivo, está pronto, 
                // e também se o modelo que estamos interessados está pronto
                System.out.println("Verificando se o servidor está pronto..");
                ServerReadyResponse serverReadyResponse = MLServer.serverReady(ServerReadyRequest.newBuilder().build());
                if (!serverReadyResponse.getReady()) {
                    System.out.println("Servidor respondeu que não está pronto!");
                    Thread.sleep(10000);
                    continue;
                } else {
                    System.out.println("Servidor está pronto!");
                }
                
                System.out.println("Verificando se o modelo está pronto..");
                ModelReadyRequest modelReadyRequest = ModelReadyRequest.newBuilder()
                        .setName("classificador-produtos")
                        .setVersion("1.1")
                        .build();
                ModelReadyResponse modelReadyResponse = MLServer.modelReady(modelReadyRequest);
                if (!modelReadyResponse.getReady()) {
                    System.out.println("Servidor respondeu que o modelo não está pronto!");
                    Thread.sleep(10000);
                    continue;
                } else {
                    System.out.println("Modelo está pronto!");
                }
                // Vamos sair do loop (por enquanto, depois faremos um controle disso)
                // completed = true;

                // Tudo parece estar funcionando, então podemos de fato fazer as requisições para o classificador de produtos:
                System.out.println("Tudo pronto, iniciando cadastro...");

                // Vamos fazer a execução recorrente, até que o usuário decida parara
                while (!completed) {
                    System.out.print("Digite a descrição do produto: ");
                    // Vamos ler a descrição do produto a partir do terminal
                    String descricao = System.console().readLine();
                    // Precisamos converter para a classe ByteString, que é a representação
                    // de dados binários do protobuf
                    ByteString descricaoBytes = ByteString.copyFromUtf8(descricao);

                    // Agora precisamos construir nossa mensagem, conforme a definição
                    // do arquivo .proto. A estrutura é a seguinte (confira lá no .proto):
                    // - ModelInferRequest: (um request)
                    //   - InferInputTensor: (pode ter vários inputs)
                    //     - InferTensorContents (que pode ter vários conteúdos)
                    // Para reproduzir essa estrutura, basta utilizar os "builders" das
                    // respectivas classes geradas a partir do .proto. Além dessa hierarquia
                    // vamos definir também os metadados, como o nome, shape e tipo de dados
                    // Compare com a requisição JSON equivalente:
                    // {
                    //    "inputs": [
                    //        {
                    //            "name": "predict",
                    //            "shape": [1],
                    //            "datatype": "BYTES",
                    //            "data": [ "boneca" ]
                    //        }
                    //    ]
                    // }
                    // Porém, há uma diferença: quando usamos GRPC, é necessário
                    // definir que o shape tem um tamanho equivalente ao número de bytes
                    // da string, e não a quantidade de strings.
                    // Também é necessário acrescentar um parâmetro que
                    // define explicitamente que estamos enviando uma string, e não
                    // uma lista de bytes. O parâmetro se chama "content_type", e o
                    // valor deve ser "str". Veja a seguir:
                    InferTensorContents contents = InferTensorContents.newBuilder()
                            .addBytesContents(descricaoBytes).build();

                    InferInputTensor input = InferInputTensor.newBuilder()
                            .setName("predict")
                            .setDatatype("BYTES")
                            .addShape(descricaoBytes.size())
                            .putParameters("content_type",
                                    InferParameter.newBuilder().setStringParam("str").build())
                            .setContents(contents)
                            .build();

                    ModelInferRequest modelInferRequest = ModelInferRequest.newBuilder()
                            .setModelName("classificador-produtos")
                            .setModelVersion("1.1")
                            .addInputs(input)
                            .build();

                    // Agora basta enviar a requisição e obter a resposta. Como estamos
                    // utilizando um "blocking" stub, essa chamada fica esperando o retorno
                    ModelInferResponse modelInferResponse = MLServer.modelInfer(modelInferRequest);
                    // A categoria inferida vem na mesma estrutura definida no protocolo V2
                    String categoria = modelInferResponse.getOutputs(0).getContents().getBytesContents(0)
                            .toStringUtf8();
                    // A seguir temos a lógica final desse aplicativo. Nada muito complicado.
                    System.out.println("A categoria do produto é "  + categoria);
                    System.out.print("Deseja cadastrar mais algum produto? (s/n) ");
                    String continua = System.console().readLine();
                    if (!continua.equalsIgnoreCase("s")) {
                        completed = true;
                    }
                }

            } catch (StatusRuntimeException e) {
                // Caso aconteça algum erro (por exemplo, um timeout)
                // Vamos esperar 10 segundos e tentar novamente depois disso
                e.printStackTrace();
                System.out.println("Servidor não está respondendo...");
                Thread.sleep(10000);
                continue;
            }
        } while (!completed);
    }
}