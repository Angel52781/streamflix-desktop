# TMDb setup

Streamflix uses a bring-your-own-credential model for TMDb catalog metadata. Do not share a real API key or access token in an issue, screenshot, log, chat message or commit.

## Get a TMDb credential

1. [Create a TMDb account](https://www.themoviedb.org/signup) or [log in](https://www.themoviedb.org/login).
2. Open your TMDb account **Settings**, then select **API**. The direct page is [the TMDb API settings page](https://www.themoviedb.org/settings/api).
3. Accept TMDb's current terms and complete the API application shown by TMDb.
4. From the API settings page, copy either the **API Key (v3 auth)** or the **API Read Access Token**. Streamflix accepts either credential; do not put its real value in this documentation or any public file.

TMDb documents the application process in its [Getting Started guide](https://developer.themoviedb.org/docs/getting-started) and the two supported application authentication values in its [Application authentication guide](https://developer.themoviedb.org/docs/authentication-application).

## Configure Streamflix

1. Open Streamflix and select **Ajustes**.
2. Open the **TMDb** tab.
3. Paste the API Key v3 or API Read Access Token into the credential field.
4. Select **Probar credencial** and wait for the successful connection message.
5. Select **Guardar**.

The saved credential is local to the Windows user. Keep it private and revoke or replace it from TMDb if it is exposed.

## Usage terms

TMDb's official FAQ describes its developer API as free for non-commercial use when TMDb is properly attributed. TMDb defines commercial projects separately and its API terms require a separate written commercial agreement for commercial use. Whether a particular project is commercial is TMDb's determination, so review the current official material rather than relying on this summary:

- [TMDb API FAQ](https://developer.themoviedb.org/docs/faq)
- [TMDb API Terms of Use](https://www.themoviedb.org/api-terms-of-use)
