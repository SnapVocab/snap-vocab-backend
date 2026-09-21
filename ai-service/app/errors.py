"""Structured errors: every failure leaves as {"error": {"code", "message"}}.

The backend branches on `code`, never on the HTTP status or the message text, so
codes are part of the contract and must not be renamed casually.
"""
from __future__ import annotations

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

logger = logging.getLogger("ai-service")

INVALID_REQUEST = "INVALID_REQUEST"      # 400: malformed request (e.g. no `file` field)
INVALID_IMAGE = "INVALID_IMAGE"          # 400/413/415: unsupported type, too large, undecodable
UNAUTHORIZED = "UNAUTHORIZED"            # 401: missing or wrong X-Service-Token
MODEL_NOT_READY = "MODEL_NOT_READY"      # 503: models still loading, retry shortly
MODEL_ERROR = "MODEL_ERROR"              # 500: inference blew up


class ServiceError(Exception):
    def __init__(self, status_code: int, code: str, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message


def error_body(code: str, message: str) -> dict:
    return {"error": {"code": code, "message": message}}


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(ServiceError)
    async def _service_error(_: Request, exc: ServiceError) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content=error_body(exc.code, exc.message))

    @app.exception_handler(RequestValidationError)
    async def _validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
        return JSONResponse(status_code=400, content=error_body(INVALID_REQUEST, str(exc.errors())))

    @app.exception_handler(Exception)
    async def _unexpected(request: Request, exc: Exception) -> JSONResponse:
        logger.exception("Unhandled error on %s", request.url.path)
        return JSONResponse(status_code=500, content=error_body(MODEL_ERROR, "Internal error during detection."))
