### Rabbitmq definitions.json:
```json
{
  "users": [
    {
      "hashing_algorithm": "rabbit_password_hashing_sha256",
      "limits": {},
      "name": "franco",
      "password_hash": "b6P/bU77X0Ul/NG+1etfrKxf8otTP6xHsq+1DU8T5sF8t22B",
      "tags": "administrator"
    }
  ],
  "vhosts": [
    {
      "limits": [],
      "metadata": { "description": "Default virtual host", "tags": [] },
      "name": "/"
    }
  ],
  "permissions": [
    {
      "configure": ".*",
      "read": ".*",
      "user": "franco",
      "vhost": "/",
      "write": ".*"
    }
  ],
  "queues": [
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "servidor",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "servidor.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.1",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.1.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.3",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.3.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.4",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.4.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.5",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.5.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.6",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.6.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.7",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.7.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.8",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.8.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.9",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.9.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.10",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.10.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.11",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.11.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.12",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.12.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.13",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.13.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.14",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.14.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.16",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.16.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.17",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.17.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.18",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.18.reply.to",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.19",
      "type": "classic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "auto_delete": false,
      "durable": true,
      "name": "filial.19.reply.to",
      "type": "classic",
      "vhost": "/"
    }
  ],
  "exchanges": [],
  "bindings": [
    {
      "arguments": {},
      "destination": "servidor",
      "destination_type": "queue",
      "routing_key": "servidor",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "servidor.reply.to",
      "destination_type": "queue",
      "routing_key": "servidor.reply.to",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.1",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.1",
      "destination_type": "queue",
      "routing_key": "filial.1",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.3",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.3",
      "destination_type": "queue",
      "routing_key": "filial.3",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.4",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.4",
      "destination_type": "queue",
      "routing_key": "filial.4",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.5",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.5",
      "destination_type": "queue",
      "routing_key": "filial.5",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.6",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.6",
      "destination_type": "queue",
      "routing_key": "filial.6",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.7",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.7",
      "destination_type": "queue",
      "routing_key": "filial.7",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.8",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.8",
      "destination_type": "queue",
      "routing_key": "filial.8",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.9",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.9",
      "destination_type": "queue",
      "routing_key": "filial.9",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.10",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.10",
      "destination_type": "queue",
      "routing_key": "filial.10",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.11",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.11",
      "destination_type": "queue",
      "routing_key": "filial.11",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.12",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.12",
      "destination_type": "queue",
      "routing_key": "filial.12",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.13",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.13",
      "destination_type": "queue",
      "routing_key": "filial.13",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.14",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.14",
      "destination_type": "queue",
      "routing_key": "filial.14",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.16",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.16",
      "destination_type": "queue",
      "routing_key": "filial.16",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.17",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.17",
      "destination_type": "queue",
      "routing_key": "filial.17",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.18",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.18",
      "destination_type": "queue",
      "routing_key": "filial.18",
      "source": "amq.direct",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.19",
      "destination_type": "queue",
      "routing_key": "filial",
      "source": "amq.topic",
      "vhost": "/"
    },
    {
      "arguments": {},
      "destination": "filial.19",
      "destination_type": "queue",
      "routing_key": "filial.19",
      "source": "amq.direct",
      "vhost": "/"
    }
  ],
  "parameters": [
    {
      "component": "shovel",
      "name": "filial.1",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.1",
        "dest-uri": "amqp://franco:franco@172.25.1.1",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.1",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.1",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.1"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "filial.3",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.3",
        "dest-uri": "amqp://franco:franco@172.25.1.3",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.3",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.3",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.3"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "filial.4",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.4",
        "dest-uri": "amqp://franco:franco@172.25.1.4",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.4",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.4",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.4"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "filial.5",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.5",
        "dest-uri": "amqp://franco:franco@172.25.1.5",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.5",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.5",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.5"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "filial.6",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.6",
        "dest-uri": "amqp://franco:franco@172.25.1.6",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.6",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.6",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.6"
      },
      "vhost": "/"
    },

    {
      "component": "shovel",
      "name": "filial.7",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.7",
        "dest-uri": "amqp://franco:franco@172.25.1.7",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.7",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.7",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.7"
      },
      "vhost": "/"
    },

    {
      "component": "shovel",
      "name": "filial.8",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.8",
        "dest-uri": "amqp://franco:franco@172.25.1.8",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.8",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.8",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.8"
      },
      "vhost": "/"
    },

    {
      "component": "shovel",
      "name": "filial.9",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.9",
        "dest-uri": "amqp://franco:franco@172.25.1.9",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.9",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.9",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.9"
      },
      "vhost": "/"
    },

    {
      "component": "shovel",
      "name": "filial.10",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.10",
        "dest-uri": "amqp://franco:franco@172.25.1.10",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.10",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.10",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.10"
      },
      "vhost": "/"
    },

    {
      "component": "shovel",
      "name": "filial.11",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.11",
        "dest-uri": "amqp://franco:franco@172.25.1.11",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.11",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.11",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.11"
      },
      "vhost": "/"
    },

    {
      "component": "shovel",
      "name": "filial.12",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.12",
        "dest-uri": "amqp://franco:franco@172.25.1.12",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.12",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.12",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.12"
      },
      "vhost": "/"
    },

    {
      "component": "shovel",
      "name": "filial.13",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.13",
        "dest-uri": "amqp://franco:franco@172.25.1.13",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.13",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.13",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.13"
      },
      "vhost": "/"
    },

    {
      "component": "shovel",
      "name": "filial.14",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.14",
        "dest-uri": "amqp://franco:franco@172.25.1.14",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.14",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.14",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.14"
      },
      "vhost": "/"
    },

    {
      "component": "shovel",
      "name": "filial.16",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.16",
        "dest-uri": "amqp://franco:franco@172.25.1.16",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.16",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.16",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.16"
      },
      "vhost": "/"
    },

    {
      "component": "shovel",
      "name": "filial.17",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.17",
        "dest-uri": "amqp://franco:franco@172.25.1.17",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.17",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.17",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.17"
      },
      "vhost": "/"
    },

    {
      "component": "shovel",
      "name": "filial.18",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.18",
        "dest-uri": "amqp://franco:franco@172.25.1.18",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.18",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.18",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.18"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "filial.19",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "filial.19",
        "dest-uri": "amqp://franco:franco@172.25.1.19",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "filial.19",
        "src-uri": "amqp://franco:franco@localhost"
      },
      "vhost": "/"
    },
    {
      "component": "shovel",
      "name": "servidor-filial.19",
      "value": {
        "ack-mode": "on-confirm",
        "dest-add-forward-headers": false,
        "dest-protocol": "amqp091",
        "dest-queue": "servidor",
        "dest-uri": "amqp://franco:franco@localhost",
        "src-delete-after": "never",
        "src-protocol": "amqp091",
        "src-queue": "servidor",
        "src-uri": "amqp://franco:franco@172.25.1.19"
      },
      "vhost": "/"
    }
  ]
}
```

### Spring boot main server rabbit application.properties:

spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=franco
spring.rabbitmq.password=franco
spring.rabbitmq.publisher-confirm-type=correlated
spring.rabbitmq.publisher-returns=true
spring.rabbitmq.replyTimeout=20000
spring.rabbitmq.connection-timeout=1000
spring.rabbitmq.requested-heartbeat=1000
spring.rabbitmq.listener.simple.max-attempts=10
spring.rabbitmq.listener.simple.retry-enabled=true
spring.rabbitmq.listener.simple.retry-initial-interval=10000
spring.rabbitmq.listener.simple.retry-multiplier=2.0



### Spring boot client servers rabbit application.properties:
#rabbit
spring.rabbitmq.password=franco
spring.rabbitmq.username=franco
spring.rabbitmq.publisher-confirm-type=correlated
spring.rabbitmq.publisher-returns=true
spring.rabbitmq.defaultReplyTimeout=10000
spring.rabbitmq.replyTimeout=10000
spring.rabbitmq.connection-timeout=1000
spring.rabbitmq.requested-heartbeat=5000

### application.yml
queue: filial.20
queue-reply-to: filial.20.reply.to
sucursal-id: 20
server-url: http://localhost:8082
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: franco
    password: franco
  multirabbitmq:
    enabled: true
    connections:
      servidor:
        host: 172.25.0.200
        port: 5672
        username: franco
        password: franco

