#!/usr/bin/env python3
"""
Ephemeral Environment Cleanup Lambda Function

Automatically destroys ephemeral development environments after a specified time period.
This helps manage costs and prevents resource accumulation from development workflows.
"""

import json
import boto3
import os
from datetime import datetime, timedelta
from typing import Dict, List

def lambda_handler(event, context):
    """
    Main Lambda handler for ephemeral environment cleanup
    """
    environment_prefix = os.environ.get('ENVIRONMENT_PREFIX', '')
    auto_destroy_hours = int(os.environ.get('AUTO_DESTROY_HOURS', '24'))
    
    if not environment_prefix:
        return {
            'statusCode': 400,
            'body': json.dumps({'error': 'ENVIRONMENT_PREFIX not configured'})
        }
    
    print(f"Starting cleanup for environment prefix: {environment_prefix}")
    print(f"Auto-destroy threshold: {auto_destroy_hours} hours")
    
    cleanup_results = {
        'environment_prefix': environment_prefix,
        'auto_destroy_hours': auto_destroy_hours,
        'cleanup_timestamp': datetime.utcnow().isoformat(),
        'resources_cleaned': []
    }
    
    try:
        # Check if environment is old enough to be cleaned up
        if not should_cleanup_environment(environment_prefix, auto_destroy_hours):
            cleanup_results['action'] = 'skipped'
            cleanup_results['reason'] = 'Environment not old enough for cleanup'
            return {
                'statusCode': 200,
                'body': json.dumps(cleanup_results)
            }
        
        # Cleanup DynamoDB tables
        cleanup_dynamodb_tables(environment_prefix, cleanup_results)
        
        # Cleanup S3 buckets
        cleanup_s3_buckets(environment_prefix, cleanup_results)
        
        # Cleanup EventBridge custom buses
        cleanup_eventbridge_buses(environment_prefix, cleanup_results)
        
        # Cleanup CloudWatch log groups
        cleanup_cloudwatch_logs(environment_prefix, cleanup_results)
        
        cleanup_results['action'] = 'completed'
        cleanup_results['status'] = 'success'
        
        print(f"Cleanup completed successfully: {cleanup_results}")
        
        return {
            'statusCode': 200,
            'body': json.dumps(cleanup_results)
        }
        
    except Exception as e:
        error_message = f"Cleanup failed: {str(e)}"
        print(error_message)
        cleanup_results['action'] = 'failed'
        cleanup_results['error'] = error_message
        
        return {
            'statusCode': 500,
            'body': json.dumps(cleanup_results)
        }

def should_cleanup_environment(environment_prefix: str, auto_destroy_hours: int) -> bool:
    """
    Check if the environment is old enough to be cleaned up based on resource creation time
    """
    try:
        # Check DynamoDB table creation time as a proxy for environment age
        dynamodb = boto3.client('dynamodb')
        
        tables = dynamodb.list_tables()['TableNames']
        env_tables = [table for table in tables if table.startswith(environment_prefix)]
        
        if not env_tables:
            print(f"No tables found for environment {environment_prefix}, considering for cleanup")
            return True
        
        # Check the creation time of the first table found
        table_name = env_tables[0]
        table_info = dynamodb.describe_table(TableName=table_name)
        creation_time = table_info['Table']['CreationDateTime']
        
        # Calculate age
        age = datetime.utcnow() - creation_time.replace(tzinfo=None)
        age_hours = age.total_seconds() / 3600
        
        print(f"Environment age: {age_hours:.2f} hours (threshold: {auto_destroy_hours} hours)")
        
        return age_hours >= auto_destroy_hours
        
    except Exception as e:
        print(f"Error checking environment age: {str(e)}, proceeding with cleanup")
        return True

def cleanup_dynamodb_tables(environment_prefix: str, results: Dict) -> None:
    """
    Delete DynamoDB tables matching the environment prefix
    """
    dynamodb = boto3.client('dynamodb')
    
    try:
        tables = dynamodb.list_tables()['TableNames']
        env_tables = [table for table in tables if table.startswith(environment_prefix)]
        
        for table_name in env_tables:
            try:
                print(f"Deleting DynamoDB table: {table_name}")
                dynamodb.delete_table(TableName=table_name)
                results['resources_cleaned'].append({
                    'type': 'dynamodb_table',
                    'name': table_name,
                    'status': 'deleted'
                })
            except Exception as e:
                print(f"Error deleting table {table_name}: {str(e)}")
                results['resources_cleaned'].append({
                    'type': 'dynamodb_table',
                    'name': table_name,
                    'status': 'error',
                    'error': str(e)
                })
                
    except Exception as e:
        print(f"Error listing DynamoDB tables: {str(e)}")

def cleanup_s3_buckets(environment_prefix: str, results: Dict) -> None:
    """
    Delete S3 buckets matching the environment prefix
    """
    s3 = boto3.client('s3')
    
    try:
        buckets = s3.list_buckets()['Buckets']
        env_buckets = [bucket['Name'] for bucket in buckets if bucket['Name'].startswith(environment_prefix)]
        
        for bucket_name in env_buckets:
            try:
                print(f"Deleting S3 bucket: {bucket_name}")
                
                # Delete all objects in bucket first
                try:
                    objects = s3.list_objects_v2(Bucket=bucket_name)
                    if 'Contents' in objects:
                        delete_keys = [{'Key': obj['Key']} for obj in objects['Contents']]
                        s3.delete_objects(
                            Bucket=bucket_name,
                            Delete={'Objects': delete_keys}
                        )
                except Exception as e:
                    print(f"Error deleting objects from bucket {bucket_name}: {str(e)}")
                
                # Delete the bucket
                s3.delete_bucket(Bucket=bucket_name)
                results['resources_cleaned'].append({
                    'type': 's3_bucket',
                    'name': bucket_name,
                    'status': 'deleted'
                })
                
            except Exception as e:
                print(f"Error deleting bucket {bucket_name}: {str(e)}")
                results['resources_cleaned'].append({
                    'type': 's3_bucket',
                    'name': bucket_name,
                    'status': 'error',
                    'error': str(e)
                })
                
    except Exception as e:
        print(f"Error listing S3 buckets: {str(e)}")

def cleanup_eventbridge_buses(environment_prefix: str, results: Dict) -> None:
    """
    Delete EventBridge custom buses matching the environment prefix
    """
    events = boto3.client('events')
    
    try:
        buses = events.list_event_buses()['EventBuses']
        env_buses = [bus['Name'] for bus in buses if bus['Name'].startswith(environment_prefix)]
        
        for bus_name in env_buses:
            try:
                print(f"Deleting EventBridge bus: {bus_name}")
                events.delete_event_bus(Name=bus_name)
                results['resources_cleaned'].append({
                    'type': 'eventbridge_bus',
                    'name': bus_name,
                    'status': 'deleted'
                })
            except Exception as e:
                print(f"Error deleting EventBridge bus {bus_name}: {str(e)}")
                results['resources_cleaned'].append({
                    'type': 'eventbridge_bus',
                    'name': bus_name,
                    'status': 'error',
                    'error': str(e)
                })
                
    except Exception as e:
        print(f"Error listing EventBridge buses: {str(e)}")

def cleanup_cloudwatch_logs(environment_prefix: str, results: Dict) -> None:
    """
    Delete CloudWatch log groups matching the environment prefix
    """
    logs = boto3.client('logs')
    
    try:
        paginator = logs.get_paginator('describe_log_groups')
        
        for page in paginator.paginate():
            log_groups = page['logGroups']
            env_log_groups = [lg['logGroupName'] for lg in log_groups if environment_prefix in lg['logGroupName']]
            
            for log_group_name in env_log_groups:
                try:
                    print(f"Deleting CloudWatch log group: {log_group_name}")
                    logs.delete_log_group(logGroupName=log_group_name)
                    results['resources_cleaned'].append({
                        'type': 'cloudwatch_log_group',
                        'name': log_group_name,
                        'status': 'deleted'
                    })
                except Exception as e:
                    print(f"Error deleting log group {log_group_name}: {str(e)}")
                    results['resources_cleaned'].append({
                        'type': 'cloudwatch_log_group',
                        'name': log_group_name,
                        'status': 'error',
                        'error': str(e)
                    })
                    
    except Exception as e:
        print(f"Error listing CloudWatch log groups: {str(e)}")

if __name__ == "__main__":
    # For local testing
    test_event = {}
    test_context = {}
    
    # Set environment variables for testing
    os.environ['ENVIRONMENT_PREFIX'] = 'test-ephemeral-dev-feature'
    os.environ['AUTO_DESTROY_HOURS'] = '1'
    
    result = lambda_handler(test_event, test_context)
    print(json.dumps(result, indent=2))