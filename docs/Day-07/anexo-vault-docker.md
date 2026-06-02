# Anexo: levantar Vault en local (opcional, para casa)

> Este anexo es **opcional** y no se monta en clase. Quien quiera practicar la integración con Vault puede levantarlo en su máquina siguiendo estos pasos. El Vault en modo dev arranca desellado y con un token fijo: sirve para aprender, **nunca para producción**.

## docker-compose.yml

```yaml
# docker-compose.yml — Vault en modo dev, SOLO para aprendizaje
services:
  vault:
    image: hashicorp/vault:latest
    container_name: vault-dev
    ports:
      - "8200:8200"
    environment:
      VAULT_DEV_ROOT_TOKEN_ID: "dev-root-token"
      VAULT_DEV_LISTEN_ADDRESS: "0.0.0.0:8200"
    cap_add:
      - IPC_LOCK
```

## Pasos

```bash
# 1. Levantar
docker compose up -d

# 2. Apuntar el CLI al Vault local
export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='dev-root-token'

# 3. Escribir un secreto para pizzeria
docker exec -it vault-dev vault kv put secret/pizzeria \
    spring.datasource.password=secreto-de-vault

# 4. Comprobar
docker exec -it vault-dev vault kv get secret/pizzeria
```

## Configuración del cliente

```yaml
spring:
  config:
    import: vault://
  application:
    name: pizzeria
  cloud:
    vault:
      uri: http://localhost:8200
      token: dev-root-token        # solo en dev; en prod va AppRole/K8s
      kv:
        enabled: true
        backend: secret
        default-context: pizzeria
```

## El ejercicio

Coge el `${DB_PASSWORD}` que ya se resuelve por variable de entorno y haz que lo resuelva Vault, **sin tocar una línea del código de la aplicación**.

Ese es el "ajá" del tema: la fuente del secreto es una decisión de despliegue, no de código.
