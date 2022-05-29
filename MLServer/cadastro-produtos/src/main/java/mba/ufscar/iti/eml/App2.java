package mba.ufscar.iti.eml;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import mba.ufscar.iti.eml.grpc.GRPCInferenceServiceGrpc;
import mba.ufscar.iti.eml.grpc.GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingStub;
import mba.ufscar.iti.eml.grpc.MLServer.InferTensorContents;
import mba.ufscar.iti.eml.grpc.MLServer.ModelInferRequest;
import mba.ufscar.iti.eml.grpc.MLServer.ModelInferRequest.InferInputTensor;
import mba.ufscar.iti.eml.grpc.MLServer.ModelInferResponse;

public class App2 {
    public static void main(String[] args) throws InterruptedException, IOException, URISyntaxException {
        System.out.println("Abrindo canal de comunicação com o servidor...");

        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8081)
                .usePlaintext()
                .build();
        GRPCInferenceServiceBlockingStub MLServer = GRPCInferenceServiceGrpc.newBlockingStub(channel);

        try {
            System.out.println("Digite as dimensões da sépala (comprimento,largura em cm). Ex: 5, 3.4");
            System.out.print("> ");
            String[] sepala = System.console().readLine().trim().split(",");

            System.out.println("Digite as dimensões da pétala (comprimento,largura em cm). Ex: 1.5, 0.2");
            System.out.print("> ");
            String[] petala = System.console().readLine().trim().split(",");

            float sepala_comprimento = Float.parseFloat(sepala[0].trim());
            float sepala_largura = Float.parseFloat(sepala[1].trim());
            float petala_comprimento = Float.parseFloat(petala[0].trim());
            float petala_largura = Float.parseFloat(petala[1].trim());

            InferTensorContents contents = InferTensorContents.newBuilder()
                .addFp32Contents(sepala_comprimento)
                .addFp32Contents(sepala_largura)
                .addFp32Contents(petala_comprimento)
                .addFp32Contents(petala_largura)
                .build();

            InferInputTensor input = InferInputTensor.newBuilder()
                    .setName("predict")
                    .setDatatype("FP32")
                    .addShape(1)
                    .addShape(4)
                    .setContents(contents)
                    .build();

            ModelInferRequest modelInferRequest = ModelInferRequest.newBuilder()
                    .setModelName("iris-qda")
                    .setModelVersion("1.0")
                    .addInputs(input)
                    .build();

            ModelInferResponse modelInferResponse = MLServer.modelInfer(modelInferRequest);
            InferTensorContents contentsResponse = modelInferResponse.getOutputs(0).getContents();
            int categoria = contentsResponse.getIntContents(0);
            System.out.println("A categoria inferida é "+categoria);
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}