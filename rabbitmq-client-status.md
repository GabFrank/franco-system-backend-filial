sudo rabbitmqctl status
Status of node rabbit@localhost ...
Runtime

OS PID: 175270
OS: Linux
Uptime (seconds): 54508
Is under maintenance?: false
RabbitMQ version: 3.12.4
RabbitMQ release series support status: supported
Node name: rabbit@localhost
Erlang configuration: Erlang/OTP 25 [erts-13.2.2.2] [source] [64-bit] [smp:8:8] [ds:8:8:10] [async-threads:1] [jit:ns]
Crypto library: OpenSSL 3.0.9 30 May 2023
Erlang processes: 423 used, 1048576 limit
Scheduler run queue: 1
Cluster heartbeat timeout (net_ticktime): 60

Plugins

Enabled plugin file: /etc/rabbitmq/enabled_plugins
Enabled plugins:

 * rabbitmq_shovel_management
 * rabbitmq_shovel
 * amqp10_client
 * rabbitmq_management
 * rabbitmq_management_agent
 * rabbitmq_web_dispatch
 * amqp_client
 * cowboy
 * cowlib

Data directory

Node data directory: /var/lib/rabbitmq/mnesia/rabbit@localhost
Raft data directory: /var/lib/rabbitmq/mnesia/rabbit@localhost/quorum/rabbit@localhost

Config files


Log file(s)

 * /var/log/rabbitmq/rabbit@localhost.log
 * <stdout>

Alarms

(none)

Memory

Total memory used: 0.0463 gb
Calculation strategy: rss
Memory high watermark setting: 0.4 of available memory, computed to: 1.5628 gb

code: 0.036 gb (41.83 %)
other_proc: 0.0196 gb (22.76 %)
other_system: 0.0178 gb (20.67 %)
binary: 0.0037 gb (4.31 %)
other_ets: 0.0026 gb (3.07 %)
plugins: 0.0021 gb (2.47 %)
atom: 0.0015 gb (1.71 %)
metrics: 0.0011 gb (1.26 %)
msg_index: 0.0005 gb (0.62 %)
mgmt_db: 0.0005 gb (0.54 %)
connection_other: 0.0003 gb (0.33 %)
connection_readers: 0.0002 gb (0.22 %)
mnesia: 0.0001 gb (0.09 %)
queue_procs: 0.0 gb (0.05 %)
quorum_ets: 0.0 gb (0.03 %)
connection_channels: 0.0 gb (0.02 %)
connection_writers: 0.0 gb (0.01 %)
quorum_queue_dlx_procs: 0.0 gb (0.0 %)
quorum_queue_procs: 0.0 gb (0.0 %)
stream_queue_procs: 0.0 gb (0.0 %)
stream_queue_replica_reader_procs: 0.0 gb (0.0 %)
allocated_unused: 0.0 gb (0.0 %)
queue_slave_procs: 0.0 gb (0.0 %)
reserved_unallocated: 0.0 gb (0.0 %)
stream_queue_coordinator_procs: 0.0 gb (0.0 %)

File Descriptors

Total: 7, limit: 32671
Sockets: 3, limit: 29401

Free Disk Space

Low free disk space watermark: 0.05 gb
Free disk space: 227.1703 gb

Totals

Connection count: 3
Queue count: 2
Virtual host count: 1

Listeners

Interface: [::], port: 15672, protocol: http, purpose: HTTP API
Interface: [::], port: 25672, protocol: clustering, purpose: inter-node and CLI tool communication
Interface: [::], port: 5672, protocol: amqp, purpose: AMQP 0-9-1 and AMQP 1.0
