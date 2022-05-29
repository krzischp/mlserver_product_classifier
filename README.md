Before to start, you also need to download the BERT model [here](https://drive.google.com/file/d/1d4RBMWwnzFBaPL2TSu-X1lfb3GLv1Pz5/view) and to save it in the following folder: `MLServer/models/identificador-sentimentos/1.0/best_model_state.bin`.

```bash
conda deactivate

eval "$(pyenv init -)"
eval "$(pyenv virtualenv-init -)"

pyenv activate classificador-produtos
```

```bash
/Users/pierre.krzisch/.pyenv/versions/3.9.6/envs/classificador-produtos/bin/pip install notebook

/Users/pierre.krzisch/.pyenv/versions/3.9.6/envs/classificador-produtos/bin/jupyter notebook
```

```bash
cd MLServer
docker-compose up --build
```

# Maven

## Java version
Para rodar, precisa do `Java 17`.

Baixar e extrair os arquivos do `Java 17`:

```bash
tar xzvf jdk-17_macos-x64_bin.tar.gz
rm jdk-17_macos-x64_bin.tar.gz
```

Adicionar no seu `PATH`
```bash
export PATH=$PATH:$HOME/dev/perso/jdk/jdk-17.0.3.1.jdk/Contents/Home/bin
```

Ou se voce ja tiver uma outra versao do `Java` no seu PATH, tambem pode substituir, como no exemplo em baixo:
```bash
which java
```
`/Users/pierre.krzisch/.jenv/shims/java`
O novo valor do seu `PATH`:
```bash
echo $PATH
export PATH=/Users/pierre.krzisch/miniforge3/bin:/Users/pierre.krzisch/miniforge3/condabin:/Users/pierre.krzisch/dev/nu/nucli:/Users/pierre.krzisch/.rbenv/shims:/Users/pierre.krzisch/.jenv/bin:/usr/local/sbin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/MacGPG2/bin:/usr/local/opt/fzf/bin:/Users/pierre.krzisch/dev/perso/jdk/jdk-17.0.3.1.jdk/Contents/Home/bin
```
(ou seja, remove `/Users/pierre.krzisch/.jenv/shims` do seu `PATH` e adiciona o caminho da nova versao: `/Users/pierre.krzisch/dev/perso/jdk/jdk-17.0.3.1.jdk/Contents/Home/bin`)


## Maven installation

```bash
export PATH=$PATH:$HOME/dev/perso/maven/apache-maven-3.8.5/bin
mvn -v
mvn archetype:generate -DarchetypeGroupId=org.apache.maven.archetypes -DarchetypeArtifactId=maven-archetype-quickstart -DarchetypeVersion=1.4
```

Esse comando irá criar um novo projeto. Serão feitas algumas perguntas. Utilize as seguintes respostas, para seguir com este exemplo:
```bash
Define value for property 'groupId': mba.ufscar.iti.eml
Define value for property 'artifactId': cadastro-produtos
Define value for property 'version' 1.0-SNAPSHOT: :
Define value for property 'package' mba.ufscar.iti.eml: :
```

## Protobuf configuration
Depois de ter adicionado o arquivo seguinte (proto format):
`MLServer/cadastro-produtos/src/main/proto/MLServer.proto`

Agora já podemos utilizar o Maven para gerar o código. Execute o seguinte comando (você precisa estar na mesma pasta onde o arquivo `pom.xml` está):
```bash
mvn generate-sources
```
Veja como será criada, na pasta target do projeto, o arquivo `generated-sources/protobuf/grpc-java/mba/ufscar/iti/eml/grpc/GRPCInferenceServiceGrpc.java`. Esse arquivo contém todo o código de comunicação com o servidor, já contendo a lógica das mensagens e os formatos estabelecidos. Veja também como o pacote foi configurado para ser o mesmo de nosso projeto, graças ao código que introduzimos no arquivo `.proto`.

O que precisamos fazer é utilizar essa classe em nosso cliente para fazer as chamadas.

Abra o arquivo `App.java` e vamos começar. Primeiro vamos fazer um código simples, que fica esperando o servidor ficar "vivo" para seguir adiante...  
Já podemos testar. Execute o seguinte comando:
```bash
mvn compile exec:java "-Dexec.mainClass=mba.ufscar.iti.eml.App"
```
Esse comando já ativa a compilação e execução da classe principal (aquela que tem o método main()).


Da mesma forma, poderíamos fazer requisições similares aos outros modelos. Por exemplo, uma requisição para um dos modelos do dataset Iris poderia ser feita também, tomando-se o cuidado de construir a requisição e a resposta corretamente (`App2.java`)
```bash
mvn compile exec:java "-Dexec.mainClass=mba.ufscar.iti.eml.App2"
```
