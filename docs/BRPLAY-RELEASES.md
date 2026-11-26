# BRPLAY: assinatura e atualizacoes Android

## Estado

Android e TV usam o mesmo APK. O atualizador consulta somente marleyjr/BrPlay-release:
primeiro o anexo opcional latest.json; se ausente, a API de Releases do mesmo repositorio.
Nao e preciso criar latest.json para publicar. Use um repositorio publico, sem token no APK.
Uma release sem APK compativel nao oferece atualizacao. Sem release publicada, a consulta manual
pode mostrar erro; isso nao impede o catalogo ou a reproducao.

Atualizacoes ficam desligadas em debug, preview e builds F-Droid. No release normal, ficam
habilitadas quando keystore.properties esta configurado. Doacoes originais continuam desligadas.
Desktop continua sem atualizacao automatica: o instalador Windows precisa de auditoria separada.
Web nao muda. Nenhuma publicacao e feita por estas alteracoes.

## Chave permanente na VPS

Se ja houver uma chave de release, reutilize-a. Nao sobrescreva chaves existentes.
Confira primeiro se /root/apk/keystore.properties ou /root/brplay-signing/brplay-release.jks existem.
Se nao houver chave anterior, execute na VPS com o JDK instalado:

```bash
mkdir -p /root/brplay-signing
chmod 700 /root/brplay-signing
keytool -genkeypair -v -keystore /root/brplay-signing/brplay-release.jks -storetype JKS -alias brplay -keyalg RSA -keysize 3072 -validity 10000
chmod 600 /root/brplay-signing/brplay-release.jks
```

As senhas sao solicitadas interativamente. Guarde uma copia protegida da chave e das senhas
fora da VPS. Nao envie a chave nem senhas para chat, GitHub ou dentro do ZIP do projeto.
Crie /root/apk/keystore.properties usando um editor, preenchendo seus valores reais:

```properties
storeFile=/root/brplay-signing/brplay-release.jks
storePassword=SUA_SENHA_DO_ARQUIVO
keyAlias=brplay
keyPassword=SUA_SENHA_DA_CHAVE
```

Proteja o arquivo com chmod 600. Senhas em arquivos .properties exigem escape de barras
invertidas; nao copie a sintaxe de aspas do shell para os valores.

Uma chave nova NAO atualiza o APK debug ja instalado, que tem outra assinatura. Planeje a
transicao: use o backup de configuracoes do app antes de desinstalar o debug e saiba que sera
necessario entrar novamente no Telegram. Desinstalar remove os dados privados e downloads do app.
Nao desinstale automaticamente e nao use a chave debug como assinatura de distribuicao.

## Compilar e validar

```bash
cd /root/apk
./gradlew :core:jvmTest --tests 'com.tmplayer.data.UpdateFeedTest' --no-daemon --max-workers=1
./gradlew :app:assembleRelease -PtmVersionName=1.0.0 --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'
find app/build/outputs/apk/release -maxdepth 1 -name '*.apk' -ls
```

Exija BUILD SUCCESSFUL. Verifique o APK com apksigner verify --verbose --print-certs usando
o apksigner do Android SDK. Nao publique um arquivo unsigned. Teste abertura, login, reproducao,
downloads e navegacao por controle remoto. Guarde tambem app/build/outputs/mapping/release/mapping.txt.

## Publicar manualmente

No repositorio marleyjr/BrPlay-release, crie uma Release com tag v1.0.0. Anexe o APK assinado
com o nome BRPLAY-1.0.0-universal.apk. O trecho universal.apk identifica o arquivo para Android/TV.
Use notas com uma lista de linhas iniciadas por '- '. Nao publique como draft para distribuicao;
pre-releases sao oferecidas apenas quando o usuario habilita versoes de teste.

Anexe tambem o codigo-fonte correspondente a essa compilacao, suas modificacoes e os avisos
de licenca, sem credenciais, banco, sessoes, keystores ou build caches. O ZIP automatico do GitHub
de um repositorio vazio de distribuicao nao contem o codigo do app. Preserve as informacoes
de fontes correspondentes das dependencias descritas em docs/RELEASING.md.

Para a proxima versao, compile com -PtmVersionName=1.0.1 e publique a tag v1.0.1 com
BRPLAY-1.0.1-universal.apk. A tag e a versao compilada precisam coincidir. Nao basta renomear
o arquivo. Mantenha a mesma chave e applicationId; o versionCode cresce pela regra do projeto.
Teste a atualizacao de 1.0.0 para 1.0.1 preservando login e configuracoes, antes da divulgacao.

O instalador continua verificando pacote, assinatura e versao. Assinar nao desativa Play Protect.
