# BRPLAY no Windows

## O que mudou

Android e desktop compartilham o catalogo, pesquisa, paginas de vinte titulos, detalhes TMDB e temporadas. O desktop abre o video recebido do bot no player nativo TDLib/mpv. Android continua usando Media3. O bot precisa estar na versao que devolve playbackTag, botId e botUsername.

O instalador Windows chama-se BRPLAY e usa a imagem existente do aplicativo. A sessao e as configuracoes do desktop ficam em pastas BRPLAY, separadas do TMPlayer original. O atualizador do projeto original esta desativado. Nomes internos de pacotes e avisos de licenca foram preservados.

## Antes de enviar

Envie a raiz completa, com gradlew.bat, gradle, app, core, ui, desktop e os arquivos de build. Nao envie somente app ou desktop.

O .gitignore exclui local.properties, arquivos .env, bot, exemplo, bancos, sessoes, logs e builds. Exclui tambem os workflows originais do primeiro envio, mantendo somente brplay-windows.yml. Isso nao remove arquivos ja rastreados: antes do primeiro commit confira `git status --short` e `git diff --cached --stat`. Nao use `git add -f` para contornar essas exclusoes. Revise tambem qualquer arquivo personalizado que contenha credenciais.

As chaves do cliente acabam incorporadas no instalador e podem ser extraidas. Repository Secrets protege o processo de build, nao transforma a chave embutida em autenticacao segura do usuario. Mantenha as verificacoes de autorizacao do servidor.

## Secrets do repositorio

Em Settings > Secrets and variables > Actions > New repository secret:

- TG_API_ID
- TG_API_HASH
- BR_CATALOG_API_URL: https://api.nexlume.vip, sem /api no final
- BR_CATALOG_API_KEY
- TMDB_API_KEY
- OMDB_API_KEY: opcional
- OPENSUBTITLES_API_KEY: opcional

Coloque somente o valor, sem aspas externas. Nenhum local.properties precisa ir ao GitHub.

## Compilar

Depois de enviar o projeto, abra Actions > BRPLAY Windows > Run workflow. O workflow e manual, instala JDK 21, executa os testes especificos do catalogo e gera o MSI 1.0.0. Nao publica releases nem envia alteracoes ao repositorio. Ao terminar, baixe BRPLAY-Windows-1.0.0 em Artifacts. O artefato fica disponivel por sete dias. Minutos de execucao e armazenamento seguem a cota da sua conta.

Para compilar localmente com JDK 21 e WiX 3 instalados:

```powershell
.\gradlew.bat :desktop:packageReleaseMsi -PdesktopVersion=1.0.0 --no-daemon --max-workers=2
```

O MSI fica em desktop/build/compose/binaries/main-release/msi/.

## Validacao pendente

Este ambiente local tem Java 8. A compilacao Kotlin, testes JVM e geracao do instalador precisam ser executados no GitHub ou em um ambiente JDK 21. Testes adicionados nao significam testes executados.

No Windows, confira login, busca, trocar categorias/paginas, temporadas, janela pequena/maximizada, teclado Tab/Enter/Esc, abrir e voltar do player, seek, falta de rede, tentar novamente e encerrar a sessao. Teste com um video pequeno primeiro. A integracao do catalogo nao foi adaptada para web. Linux/macOS nao foram validados neste trabalho.

Ao atualizar a copia antiga na VPS, use uma copia limpa do projeto ou sincronize tambem as remocoes: CatalogApi.kt, CatalogMetadata.kt e CatalogPlayback.kt sairam de app/src/main/java/com/tmplayer/catalog. Deixar copias antigas junto com os novos arquivos de core/ui causa classes duplicadas. O CatalogScreen.kt de app agora e apenas a entrada Android.
