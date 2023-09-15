rabbitmqctl status
Status of node rabbit@rabbit-0 ...
Runtime

OS PID: 306
OS: Linux
Uptime (seconds): 8059
Is under maintenance?: false
RabbitMQ version: 3.8.34
Node name: rabbit@rabbit-0
Erlang configuration: Erlang/OTP 24 [erts-12.3.2.1] [source] [64-bit] [smp:4:4] [ds:4:4:10] [async-threads:1]
Crypto library: OpenSSL 1.1.1o  3 May 2022
Erlang processes: 1808 used, 1048576 limit
Scheduler run queue: 1
Cluster heartbeat timeout (net_ticktime): 60

Plugins

Enabled plugin file: /etc/rabbitmq/enabled_plugins
Enabled plugins:

 * rabbitmq_prometheus
 * rabbitmq_shovel_management
 * rabbitmq_shovel
 * amqp10_client
 * amqp10_common
 * prometheus
 * rabbitmq_management
 * amqp_client
 * rabbitmq_web_dispatch
 * cowboy
 * cowlib
 * rabbitmq_management_agent

Data directory

Node data directory: /var/lib/rabbitmq/mnesia/rabbit@rabbit-0
Raft data directory: /var/lib/rabbitmq/mnesia/rabbit@rabbit-0/quorum/rabbit@rabbit-0

Config files

 * /etc/rabbitmq/rabbitmq.conf

Log file(s)

 * <stdout>

Alarms

(none)

Memory

Total memory used: 0.2467 gb
Calculation strategy: rss
Memory high watermark setting: 0.4 of available memory, computed to: 9.8006 gb

binary: 0.1188 gb (37.39 %)
allocated_unused: 0.0635 gb (19.99 %)
code: 0.0342 gb (10.77 %)
other_proc: 0.028 gb (8.8 %)
other_system: 0.0269 gb (8.46 %)
connection_other: 0.0146 gb (4.6 %)
queue_procs: 0.0089 gb (2.81 %)
plugins: 0.0086 gb (2.7 %)
other_ets: 0.0036 gb (1.14 %)
connection_writers: 0.003 gb (0.94 %)
mgmt_db: 0.0027 gb (0.84 %)
atom: 0.0015 gb (0.46 %)
connection_readers: 0.0012 gb (0.37 %)
msg_index: 9.0e-4 gb (0.27 %)
connection_channels: 6.0e-4 gb (0.18 %)
metrics: 6.0e-4 gb (0.18 %)
mnesia: 3.0e-4 gb (0.09 %)
quorum_ets: 0.0 gb (0.02 %)
queue_slave_procs: 0.0 gb (0.0 %)
quorum_queue_procs: 0.0 gb (0.0 %)
reserved_unallocated: 0.0 gb (0.0 %)

File Descriptors

Total: 123, limit: 1048479
Sockets: 101, limit: 943629

Free Disk Space

Low free disk space watermark: 0.05 gb
Free disk space: 432.2327 gb

Totals

Connection count: 37
Queue count: 39
Virtual host count: 1

Listeners

Interface: [::], port: 15672, protocol: http, purpose: HTTP API
Interface: [::], port: 15692, protocol: http/prometheus, purpose: Prometheus exporter API over HTTP
Interface: [::], port: 25672, protocol: clustering, purpose: inter-node and CLI tool communication
Interface: [::], port: 5672, protocol: amqp, purpose: AMQP 0-9-1 and AMQP 1.0
