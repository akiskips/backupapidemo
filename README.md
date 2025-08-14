# Java Ad-hoc Backup Runner for Azure Data Protection

A tiny Java program that triggers an ad-hoc backup of a specific Backup Instance in an Azure Backup Vault and polls the operation to completion.

## Configuration (env vars)

- SUBSCRIPTION_ID: Azure subscription GUID
- RESOURCE_GROUP: Resource group of the backup vault
- VAULT_NAME: Backup vault name
- BACKUP_INSTANCE_NAME: Exact backup instance resource name (not friendlyName). To find the resource name, list the backup instances using the Azure CLI or REST API. 
- API_VERSION: Optional; defaults to `2025-07-01`
- BACKUP_RULE_NAME: Optional; defaults to `Default` (use your policy's AzureBackupRule name if different)
- BACKUP_TAG_NAME: Optional; defaults to `Default`
- BACKUP_TAG_ID: Optional; defaults to `Default_`



## Build

```bash
cd java-adhoc-backup
mvn -q -e -B -DskipTests package
```

## Container build

```bash
docker build -t yourrepo/java-adhoc-backup:1.0.0 .
```

## Run locally (needs az login or managed identity with Data Protection permissions)

```bash
export SUBSCRIPTION_ID=...
export RESOURCE_GROUP=rg-snapshots
export VAULT_NAME=bcvaultdemo
export BACKUP_INSTANCE_NAME=pvc-...-guid
export BACKUP_RULE_NAME=Default
java -jar target/java-adhoc-backup-1.0.0-jar-with-dependencies.jar
```

## Run container with Docker

```bash
docker run --rm \
  -e AZURE_CLIENT_ID="<appId>" \
  -e AZURE_TENANT_ID="<tenantId>" \
  -e AZURE_CLIENT_SECRET="<secret>" \
  -e SUBSCRIPTION_ID="" \
  -e RESOURCE_GROUP="" \
  -e VAULT_NAME="" \
  -e BACKUP_INSTANCE_NAME="" \
  -e BACKUP_RULE_NAME="" \
  java-adhoc-backup:1.0.0
```

## Deploy to AKS

Use a managed identity with permission on the backup vault (Data Protection Backup Instance Backup action). Assign it to this pod.

Example K8s manifest:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: adhoc-backup
spec:
  replicas: 1
  selector:
    matchLabels: { app: adhoc-backup }
  template:
    metadata:
      labels: { app: adhoc-backup }
      annotations:
        azure.workload.identity/use: "true"
    spec:
      serviceAccountName: backup-runner-sa
      containers:
        - name: runner
          image: yourrepo/java-adhoc-backup:1.0.0
          env:
            - name: SUBSCRIPTION_ID
              value: "<sub>"
            - name: RESOURCE_GROUP
              value: "rg-snapshots"
            - name: VAULT_NAME
              value: "bcvaultdemo"
            - name: BACKUP_INSTANCE_NAME
              value: "pvc-...-guid"
            - name: BACKUP_RULE_NAME
              value: "Default"
```

If not using Workload Identity, run on a node with a user-assigned managed identity and use Azure AD Pod Identity replacement compatible setup.

## Authentication Setup

### Option 1: Service Principal (Recommended for production)


# Use the more specific backup contributor role
az role assignment create \
  --assignee $SP_OBJECT_ID \
  --role "Backup Contributor" \
  --scope "/subscriptions/{subscription-id}/resourceGroups/rg-snapshots/providers/Microsoft.DataProtection/backupVaults/bcvaultdemo"
```

3. **Set environment variables for service principal authentication:**
```bash
export AZURE_CLIENT_ID="<service-principal-app-id>"
export AZURE_TENANT_ID="<tenant-id>"
export AZURE_CLIENT_SECRET="<service-principal-password>"
export SUBSCRIPTION_ID="<subscription-id>"
export RESOURCE_GROUP="rg-snapshots"
export VAULT_NAME="bcvaultdemo"
export BACKUP_INSTANCE_NAME="pvc-...-guid"
```

4. **Test token generation:**
```bash
# Using Azure CLI with service principal
az login --service-principal -u $AZURE_CLIENT_ID -p $AZURE_CLIENT_SECRET --tenant $AZURE_TENANT_ID
TOKEN=$(az account get-access-token --resource=https://management.azure.com/ --query accessToken -o tsv)


### Required Azure RBAC Permissions

The service principal or managed identity needs one of these roles on the backup vault:
- **Backup Operator**: Can trigger backups and view backup jobs
- **Backup Contributor**: Full backup management permissions
- **Custom role** with these specific permissions:
  ```json
  {
    "permissions": [
      {
        "actions": [
          "Microsoft.DataProtection/backupVaults/backupInstances/backup/action",
          "Microsoft.DataProtection/backupVaults/backupInstances/read",
          "Microsoft.DataProtection/backupVaults/backupJobs/read"
        ]
      }
    ]
  }
  ```



## Notes

- Ensure the BACKUP_INSTANCE_NAME is the actual resource name (listing `.../backupInstances` shows it), not the friendly name.
- The program polls Azure-AsyncOperation/Location until completion.
- For prod, add retries and better logging.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
