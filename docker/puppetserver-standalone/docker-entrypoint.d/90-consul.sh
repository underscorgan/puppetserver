#!/bin/sh

if [ "$CONSUL_ENABLED" = "true" ]; then
  consul agent -config-dir=/etc/consul.d -retry-join "$CONSUL_HOSTNAME" &
fi
