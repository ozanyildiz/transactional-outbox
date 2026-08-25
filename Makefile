build:
	./mvnw clean package -DskipTests

test:
	DOCKER_HOST="$${DOCKER_HOST:-$$(docker context inspect --format '{{.Endpoints.docker.Host}}')}" \
	TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="$${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-/var/run/docker.sock}" \
	./mvnw clean verify

up:
	docker compose up -d --build --wait

down:
	docker compose down -v --remove-orphans

logs:
	docker compose logs -f

clean: down
	./mvnw clean

demo: up
	@echo "Creating a payment via the FOLLOWER (app2, :8082) ..."
	PAYMENT_ID=$$(curl --fail --silent --show-error -X POST http://localhost:8082/payments -H 'Content-Type: application/json' -d '{"amount": 42.50}' | grep -o '"paymentId":"[^"]*"' | cut -d'"' -f4); \
	test -n "$$PAYMENT_ID"; \
	echo "  paymentId=$$PAYMENT_ID"; \
	sleep 1; \
	echo "Marking it succeeded ..."; \
	curl --fail --silent --show-error -o /dev/null -w '  HTTP %{http_code}\n' -X POST http://localhost:8082/payments/$$PAYMENT_ID/succeed
	@sleep 2
	@echo
	@echo "--- app1 (leader, dispatches outbox -> NATS) recent logs ---"
	docker compose logs app1 --tail 15
	@echo
	@echo "--- app2 (follower, wrote the payment) recent logs ---"
	docker compose logs app2 --tail 15

.PHONY: build test up down logs clean demo
