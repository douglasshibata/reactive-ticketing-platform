#!/usr/bin/env bash
set -e

echo "Waiting for Kafka to be ready..."
cub kafka-ready -b kafka:9092 1 30 || true

echo "Creating Kafka topics for Reactive Ticketing..."
kafka-topics --create --if-not-exists --bootstrap-server kafka:9092 --partitions 3 --replication-factor 1 --topic ticket.reserved
kafka-topics --create --if-not-exists --bootstrap-server kafka:9092 --partitions 3 --replication-factor 1 --topic ticket.paid
kafka-topics --create --if-not-exists --bootstrap-server kafka:9092 --partitions 3 --replication-factor 1 --topic ticket.checked-in

echo "Kafka topics created successfully."
