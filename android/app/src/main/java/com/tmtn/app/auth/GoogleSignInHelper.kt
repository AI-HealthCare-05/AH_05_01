package com.tmtn.app.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.tmtn.app.BuildConfig

/**
 * 구글 계정으로 로그인해서 **ID 토큰 문자열 하나**를 얻어오는 역할만 하는 헬퍼 (2026-09-09).
 *
 * 여기서 하지 않는 것:
 *  - 토큰 검증. 앱이 스스로 검증해봐야 의미가 없습니다(앱은 조작 가능). 검증은 서버가
 *    구글 공개키로 합니다(app/core/oauth/google.py).
 *  - 우리 서비스 로그인. 받아온 토큰을 서버에 보내는 건 OnboardingState.loginWithGoogle().
 *
 * ⚠️ 예전 방식(com.google.android.gms.auth.api.signin.GoogleSignIn)은 공식 deprecated이고
 * Play Services Auth SDK에서 제거될 예정이라 Credential Manager로 붙였습니다.
 *
 * ⚠️ context는 반드시 **Activity** 컨텍스트여야 합니다. 계정 선택 시트를 화면 위에 띄워야
 * 해서, applicationContext를 넘기면 실행 시점에 실패합니다.
 *
 * ⚠️ 2026-09-10 수정 (2단계 시도로 변경):
 * GetGoogleIdOption은 실패 원인을 거의 전부 NoCredentialException 하나로 뭉쳐서 돌려줍니다.
 * 기기에 계정이 정말 없을 때도, 클라이언트 ID가 잘못됐을 때도, 동의 화면이 테스트 모드라
 * 그 계정이 테스트 사용자에 없을 때도 전부 같은 예외입니다. 그래서 실기기에서 계정이
 * 멀쩡히 있는데도 "계정 없음"으로 안내되는 일이 생깁니다.
 *
 * 이제 NoCredential이 오면 GetSignInWithGoogleOption("Google로 로그인" 버튼 흐름)으로 한 번
 * 더 시도합니다. 이쪽은 "이미 이 앱에 인증된 계정" 같은 조건을 따지지 않고 계정 선택 화면을
 * 그대로 띄우기 때문에, 기기에 계정이 있으면 대개 여기서 통과합니다. 2차까지 실패하면
 * 기기 문제가 아니라 설정 문제일 가능성이 크므로 안내 문구도 다르게 나갑니다.
 */
object GoogleSignInHelper {

    private const val TAG = "GoogleSignInHelper"

    /** 구글 로그인 시도 결과. 실패 사유별로 화면에서 다르게 안내할 수 있게 나눠둠. */
    sealed interface Result {
        /** 성공 - 서버로 보낼 ID 토큰. */
        data class Success(val idToken: String) : Result

        /** 사용자가 시트를 닫음(뒤로가기 등). 에러 문구를 띄우면 안 되는 경우. */
        data object Cancelled : Result

        /** 기기에 쓸 수 있는 구글 계정이 없음 - "설정에서 계정을 추가해 주세요" 안내가 맞음. */
        data object NoGoogleAccount : Result

        /** 그 외 실패. message는 화면에 그대로 보여줄 수 있는 문구. */
        data class Failure(val message: String) : Result
    }

    fun isConfigured(): Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /**
     * 구글 계정 선택 시트를 띄우고 ID 토큰을 받아옵니다.
     *
     * filterByAuthorizedAccounts=false로 둔 이유: true면 "이 앱에 이미 로그인한 적 있는
     * 계정"만 보여줘서, 처음 쓰는 사용자에게는 시트에 아무것도 안 뜨고 NoCredential로
     * 떨어집니다. 가입과 로그인이 같은 버튼이라 항상 전체 계정을 보여주는 쪽이 맞습니다.
     *
     * autoSelectEnabled=false인 이유: 계정이 하나뿐일 때 사용자가 누르기도 전에 자동으로
     * 로그인돼 버리면, "구글로 계속하기"를 실수로 눌렀을 때 되돌릴 방법이 없습니다.
     */
    suspend fun requestIdToken(context: Context): Result {
        if (!isConfigured()) {
            Log.w(TAG, "GOOGLE_WEB_CLIENT_ID가 비어 있음 - local.properties 확인 필요")
            return Result.Failure("구글 로그인이 아직 설정되지 않았어요.")
        }

        // ⚠️ 값 자체는 비밀이 아니지만(APK에 그대로 들어감) 로그에 통째로 남길 이유는 없어서
        // 앞뒤만 남깁니다. 어떤 ID가 빌드에 박혔는지 확인하는 용도입니다.
        // 참고: 문자열만 봐서는 웹 유형인지 안드로이드 유형인지 구분할 수 없습니다.
        // 그건 Google Cloud Console의 사용자 인증 정보 목록에서만 확인됩니다.
        Log.i(TAG, "구글 로그인 시도: serverClientId=${maskClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)}")

        val googleIdRequest = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setAutoSelectEnabled(false)
                    .build()
            )
            .build()

        val first = attempt(context, googleIdRequest, "GetGoogleIdOption")
        if (first !is Result.NoGoogleAccount) return first

        // 1차가 "자격 증명 없음"으로 왔을 때만 2차를 시도합니다. 사용자가 직접 취소한
        // 경우(Cancelled)에는 시트를 다시 띄우면 안 되므로 위에서 걸러집니다.
        Log.w(TAG, "1차 실패(NoCredential) - GetSignInWithGoogleOption으로 재시도")

        val signInRequest = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
            )
            .build()

        return when (val second = attempt(context, signInRequest, "GetSignInWithGoogleOption")) {
            is Result.NoGoogleAccount -> {
                // 두 방식 모두 계정을 못 찾았습니다. 기기에 계정이 정말 없을 수도 있지만,
                // 실기기라면 대개 설정 쪽 문제입니다. 확인 순서:
                //   1) serverClientId가 **웹 애플리케이션** 유형인가 (안드로이드 유형이면 항상 실패)
                //   2) Android 유형 클라이언트에 패키지명 com.tmtn.app + 이 빌드의 SHA-1이 등록됐는가
                //   3) OAuth 동의 화면이 "테스트" 상태라면, 로그인하려는 계정이 테스트 사용자인가
                //   4) 방금 만든 클라이언트라면 반영까지 몇 분 걸릴 수 있음
                Log.e(
                    TAG,
                    "2차까지 실패 - 클라이언트 ID 유형(웹이어야 함)·SHA-1 등록·동의 화면 테스트 사용자 확인 필요",
                )
                Result.Failure("구글 계정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.")
            }

            else -> second
        }
    }

    /** 요청 한 번을 실행하고 예외를 Result로 옮김. 두 방식이 같은 처리를 쓰도록 분리. */
    private suspend fun attempt(context: Context, request: GetCredentialRequest, label: String): Result =
        try {
            val response = CredentialManager.create(context).getCredential(context, request)
            extractIdToken(response.credential)
        } catch (e: GetCredentialCancellationException) {
            // 사용자가 직접 닫은 것이므로 에러가 아님. 화면에서 아무 문구도 띄우지 않음.
            Log.i(TAG, "$label: 사용자가 취소함")
            Result.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "$label: 사용 가능한 자격 증명 없음", e)
            Result.NoGoogleAccount
        } catch (e: GetCredentialException) {
            // ⚠️ 여기로 자주 오는 실제 원인은 대부분 "설정 문제"입니다:
            // 클라이언트 ID가 틀렸거나, Google Cloud Console에 이 앱의 서명 SHA-1이
            // 등록되지 않은 경우(특히 디버그 키만 등록하고 릴리즈 빌드를 돌린 경우).
            // 사용자에게는 일반 문구를 보여주고, 원인은 로그로 남깁니다.
            Log.e(TAG, "$label: 실패 [${e.type}] ${e.errorMessage}", e)
            Result.Failure("구글 로그인에 실패했어요. 잠시 후 다시 시도해 주세요.")
        }

    private fun maskClientId(id: String): String =
        if (id.length <= 16) "(너무 짧음: ${id.length}자)" else "${id.take(8)}…${id.takeLast(28)} (${id.length}자)"

    private fun extractIdToken(credential: androidx.credentials.Credential): Result {
        val isGoogleIdToken = credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        if (!isGoogleIdToken) {
            // Credential Manager는 패스키·비밀번호 등 다른 종류도 돌려줄 수 있는 통합
            // API라, 우리가 요청한 종류가 맞는지 확인하고 넘어가야 합니다.
            Log.e(TAG, "예상과 다른 자격 증명 종류: ${credential.type}")
            return Result.Failure("구글 로그인 정보를 읽지 못했어요.")
        }

        return try {
            val googleCredential = GoogleIdTokenCredential.createFrom((credential as CustomCredential).data)
            Result.Success(googleCredential.idToken)
        } catch (e: Exception) {
            Log.e(TAG, "ID 토큰 파싱 실패", e)
            Result.Failure("구글 로그인 정보를 읽지 못했어요.")
        }
    }
}
