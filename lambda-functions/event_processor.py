import json
import boto3
import logging
from datetime import datetime, timezone
import uuid

logger = logging.getLogger()
logger.setLevel(logging.INFO)

# Initialize DynamoDB client
dynamodb = boto3.resource('dynamodb')

def lambda_handler(event, context):
    """
    Process EventBridge events for audit logging
    This is a placeholder implementation for the Java template
    """
    try:
        logger.info(f"Processing EventBridge event: {json.dumps(event)}")

        # Get table name from environment
        import os
        table_name = os.environ.get('AUDIT_TABLE_NAME')

        if not table_name:
            logger.error("AUDIT_TABLE_NAME environment variable not set")
            return {"statusCode": 500, "body": "Configuration error"}

        table = dynamodb.Table(table_name)

        # Process the event
        detail = event.get('detail', {})
        detail_type = event.get('detail-type', 'Unknown')
        source = event.get('source', 'Unknown')

        # Create audit log entry
        audit_entry = {
            'event_id': str(uuid.uuid4()),
            'timestamp': datetime.now(timezone.utc).isoformat(),
            'event_type': detail_type,
            'source': source,
            'detail': json.dumps(detail),
            'ttl': int((datetime.now(timezone.utc).timestamp() + (90 * 24 * 60 * 60)))  # 90 days TTL
        }

        # Write to DynamoDB
        table.put_item(Item=audit_entry)

        logger.info(f"Audit entry created: {audit_entry['event_id']}")

        return {
            "statusCode": 200,
            "body": json.dumps({
                "message": "Event processed successfully",
                "eventId": audit_entry['event_id']
            })
        }

    except Exception as e:
        logger.error(f"Event processing error: {str(e)}")
        return {
            "statusCode": 500,
            "body": json.dumps({
                "error": "Event processing failed",
                "message": str(e)
            })
        }