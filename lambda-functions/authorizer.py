import json
import boto3
import logging

logger = logging.getLogger()
logger.setLevel(logging.INFO)

def lambda_handler(event, context):
    """
    Simple API Key authorizer for API Gateway
    This is a placeholder implementation for the Java template
    """
    try:
        logger.info(f"Authorizer event: {json.dumps(event)}")

        # Extract API key from headers
        headers = event.get('headers', {})
        api_key = headers.get('x-api-key') or headers.get('X-API-Key')

        # Simple validation - in production, validate against stored keys
        is_authorized = api_key is not None and len(api_key) > 0

        # Return authorization response
        response = {
            "isAuthorized": is_authorized,
            "context": {
                "apiKey": api_key if is_authorized else "invalid"
            }
        }

        logger.info(f"Authorization result: {is_authorized}")
        return response

    except Exception as e:
        logger.error(f"Authorization error: {str(e)}")
        return {
            "isAuthorized": False,
            "context": {
                "error": "Authorization failed"
            }
        }