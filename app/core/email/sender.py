"""Gmail SMTP로 인증번호 이메일을 보내는 모듈.

⚠️ smtplib는 동기(블로킹) 라이브러리라, 이 모듈의 함수를 async 코드에서 직접 부르면
이벤트 루프가 막힘. 반드시 서비스 레이어에서 asyncio.to_thread(send_verification_email, ...)
로 감싸서 호출할 것 (email_verification.py에 이미 그렇게 해둠).
"""

import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from app.core import config


class EmailSendError(Exception):
    """SMTP 발송 실패 시 발생. 원인(exc)을 그대로 감싸서 상위에서 로깅할 수 있게 함."""


def _build_verification_message(to_email: str, code: str) -> MIMEMultipart:
    message = MIMEMultipart("alternative")
    message["Subject"] = f"[틈튼] 인증번호 {code}"
    message["From"] = f"{config.SMTP_FROM_NAME} <{config.SMTP_USERNAME}>"
    message["To"] = to_email

    text_body = f"틈튼(TMTN) 인증번호는 {code} 입니다.\n10분 안에 입력해 주세요.\n본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다."
    html_body = f"""
    <div style="font-family: sans-serif; padding: 24px;">
      <h2 style="color:#0C3B2E;">틈튼(TMTN) 이메일 인증</h2>
      <p>아래 인증번호를 앱에 입력해 주세요. <b>10분간</b> 유효합니다.</p>
      <div style="font-size:32px; font-weight:bold; letter-spacing:8px; margin:24px 0;">{code}</div>
      <p style="color:#666; font-size:13px;">본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.</p>
    </div>
    """

    message.attach(MIMEText(text_body, "plain"))
    message.attach(MIMEText(html_body, "html"))
    return message


def send_already_registered_email(to_email: str) -> None:
    """⚠️ 2026-09-03 리뷰 반영: 이미 가입된 이메일로 다시 가입 시도가 오면, 인증번호 대신
    이 안내 메일을 보냄(응답만 봐서는 신규 가입 요청과 구분이 안 되게 하기 위함 -
    request_email_verification 참고). 동기 함수 — asyncio.to_thread()로 감싸서 호출할 것."""

    message = MIMEMultipart("alternative")
    message["Subject"] = "[틈튼] 이미 가입된 이메일입니다"
    message["From"] = f"{config.SMTP_FROM_NAME} <{config.SMTP_USERNAME}>"
    message["To"] = to_email

    text_body = "이미 이 이메일로 가입된 틈튼(TMTN) 계정이 있습니다.\n로그인을 시도해 주세요.\n본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다."
    html_body = """
    <div style="font-family: sans-serif; padding: 24px;">
      <h2 style="color:#0C3B2E;">틈튼(TMTN) 계정 안내</h2>
      <p>이미 이 이메일로 가입된 계정이 있습니다. 로그인을 시도해 주세요.</p>
      <p style="color:#666; font-size:13px;">본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.</p>
    </div>
    """
    message.attach(MIMEText(text_body, "plain"))
    message.attach(MIMEText(html_body, "html"))

    try:
        with smtplib.SMTP(config.SMTP_HOST, config.SMTP_PORT, timeout=10) as server:
            server.starttls()
            server.login(config.SMTP_USERNAME, config.SMTP_APP_PASSWORD)
            server.sendmail(config.SMTP_USERNAME, [to_email], message.as_string())
    except smtplib.SMTPException as exc:
        raise EmailSendError(f"SMTP 발송 실패: {exc}") from exc
    except OSError as exc:
        raise EmailSendError(f"이메일 서버 연결 실패: {exc}") from exc


def send_verification_email(to_email: str, code: str) -> None:
    """동기 함수 — 반드시 asyncio.to_thread()로 감싸서 호출할 것."""

    message = _build_verification_message(to_email, code)

    try:
        with smtplib.SMTP(config.SMTP_HOST, config.SMTP_PORT, timeout=10) as server:
            server.starttls()
            server.login(config.SMTP_USERNAME, config.SMTP_APP_PASSWORD)
            server.sendmail(config.SMTP_USERNAME, [to_email], message.as_string())
    except smtplib.SMTPException as exc:
        raise EmailSendError(f"SMTP 발송 실패: {exc}") from exc
    except OSError as exc:  # 타임아웃, 네트워크 오류 등
        raise EmailSendError(f"이메일 서버 연결 실패: {exc}") from exc
