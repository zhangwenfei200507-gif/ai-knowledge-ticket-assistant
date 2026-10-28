INSERT INTO web_evidence (
    question,
    query,
    title,
    url,
    domain,
    snippet,
    extracted_content,
    suggested_article_title,
    suggested_article_content,
    category,
    credibility_score,
    status,
    evidence_origin
)
SELECT
    'What is Docker Bake and when should a team use it instead of plain docker build commands?',
    'Docker Bake Buildx official documentation',
    'Docker Bake Buildx',
    'https://docs.docker.com/build/bake/',
    'docs.docker.com',
    'Docker Bake is a Buildx feature for defining build configuration declaratively and running multiple builds concurrently.',
    'Docker Bake is a Docker Buildx feature that lets teams define build configuration in a declarative file instead of repeating long docker build command lines. Bake files can be written in HCL or JSON, and Docker Compose files can also be used as build definitions. Bake is useful when a team needs repeatable multi-target builds, shared build options, concurrent builds, or CI build orchestration. For a single simple image, plain docker build is usually easier. For a platform with multiple images, tags, platforms, cache rules, and push targets, Bake makes the build workflow easier to review and reproduce.',
    'Docker Bake Build Orchestration',
    '## Overview
Docker Bake is a Docker Buildx feature for declaring build configuration in a reusable file. It is most useful when builds have multiple targets, shared options, multi-platform output, cache configuration, or CI orchestration requirements.

## Core Concepts
- Bake replaces long, repeated docker build command lines with a declarative build definition.
- A Bake file can define multiple targets and shared build settings.
- Buildx can run specified Bake targets concurrently as part of one build invocation.
- Docker Compose files can also act as build definitions for Bake-driven workflows.

## Workflow
- Use plain docker build for a simple one-image local build.
- Use Docker Bake when the same build settings must be shared across developers or CI.
- Use Bake when multiple images, platforms, tags, cache rules, or push targets must be coordinated.

## Best Practices
- Keep Bake targets small and named by deployable artifact or build purpose.
- Use shared groups and variables to avoid repeating build settings.
- Keep secrets out of Bake files and inject sensitive values through approved secret mechanisms.',
    'Docker',
    0.96,
    'NEEDS_REVIEW',
    'WEB_SEARCH'
WHERE NOT EXISTS (
    SELECT 1 FROM web_evidence
    WHERE url = 'https://docs.docker.com/build/bake/'
      AND question = 'What is Docker Bake and when should a team use it instead of plain docker build commands?'
);

INSERT INTO web_evidence (
    question,
    query,
    title,
    url,
    domain,
    snippet,
    extracted_content,
    suggested_article_title,
    suggested_article_content,
    category,
    credibility_score,
    status,
    evidence_origin
)
SELECT
    'What is the Kubernetes ValidatingAdmissionPolicy feature used for?',
    'Kubernetes ValidatingAdmissionPolicy official documentation',
    'Kubernetes ValidatingAdmissionPolicy',
    'https://kubernetes.io/docs/reference/access-authn-authz/validating-admission-policy/',
    'kubernetes.io',
    'Validating admission policies provide a declarative, in-process alternative to validating admission webhooks using CEL.',
    'Kubernetes ValidatingAdmissionPolicy is used to declare admission validation rules that are evaluated by the Kubernetes API server before resources are accepted. It uses Common Expression Language rules and can be parameterized and scoped to specific resources. It is useful when validation can be expressed declaratively without operating an external webhook service. It does not mutate resources; it accepts or rejects API requests based on policy and binding configuration.',
    'Kubernetes Validating Admission Policy',
    '## Overview
ValidatingAdmissionPolicy lets Kubernetes administrators define admission-time validation rules directly in the API server. It provides a declarative alternative to validating admission webhooks for policies that can be expressed with CEL.

## Core Concepts
- ValidatingAdmissionPolicy evaluates create, update, and other admission requests before objects are persisted.
- Policies use Common Expression Language to express validation logic.
- Bindings connect a policy to the resources and scope where it should apply.
- The feature validates requests; it does not mutate resource objects.

## Workflow
- Use ValidatingAdmissionPolicy for cluster rules that are stable, declarative, and do not require external service calls.
- Use validating webhooks when validation requires complex logic, external data, or integration with another service.

## Best Practices
- Keep policies focused on one validation concern.
- Test CEL expressions before applying them broadly.
- Scope bindings carefully so a policy does not block unrelated workloads.',
    'Kubernetes',
    0.96,
    'NEEDS_REVIEW',
    'WEB_SEARCH'
WHERE NOT EXISTS (
    SELECT 1 FROM web_evidence
    WHERE url = 'https://kubernetes.io/docs/reference/access-authn-authz/validating-admission-policy/'
      AND question = 'What is the Kubernetes ValidatingAdmissionPolicy feature used for?'
);

INSERT INTO web_evidence (
    question,
    query,
    title,
    url,
    domain,
    snippet,
    extracted_content,
    suggested_article_title,
    suggested_article_content,
    category,
    credibility_score,
    status,
    evidence_origin
)
SELECT
    'What is the purpose of PostgreSQL logical replication failover slots?',
    'PostgreSQL logical replication failover slots official documentation',
    'PostgreSQL Logical Replication Failover',
    'https://www.postgresql.org/docs/current/logical-replication-failover.html',
    'postgresql.org',
    'Logical replication slots can be synchronized to standby servers so subscribers can continue after publisher failover.',
    'PostgreSQL logical replication failover support helps subscribers continue replication after a publisher failover. Logical slots on the primary that correspond to subscriptions can be synchronized to a standby when failover support is configured. After failover, only synchronized persistent slots that have reached synced state on the standby can be used to resume logical replication. Operators should verify subscription configuration, slot synchronization state, standby readiness, and replication lag before relying on failover continuity.',
    'PostgreSQL Logical Replication Failover Slots',
    '## Overview
Logical replication failover support is used to preserve logical replication continuity when a publisher fails over to a standby. It reduces the risk that subscribers must be reseeded after promotion.

## Core Concepts
- Logical replication slots on a primary can be synchronized to a standby for failover continuity.
- Subscriptions must be configured so the relevant logical slots are failover-capable.
- After promotion, only synchronized persistent slots that reached synced state on the standby can resume logical replication.

## Troubleshooting
- Check whether the subscription and slot were configured for failover.
- Check slot synchronization state on the standby before promotion.
- Check replication lag and confirm the standby has the WAL needed by subscribers.
- Verify subscribers reconnect to the promoted publisher endpoint after failover.

## Best Practices
- Treat logical slot failover as part of HA design, not as an after-the-fact recovery step.
- Monitor slot lag, synced state, retained WAL, and subscriber connectivity.',
    'PostgreSQL',
    0.96,
    'NEEDS_REVIEW',
    'WEB_SEARCH'
WHERE NOT EXISTS (
    SELECT 1 FROM web_evidence
    WHERE url = 'https://www.postgresql.org/docs/current/logical-replication-failover.html'
      AND question = 'What is the purpose of PostgreSQL logical replication failover slots?'
);

INSERT INTO web_evidence (
    question,
    query,
    title,
    url,
    domain,
    snippet,
    extracted_content,
    suggested_article_title,
    suggested_article_content,
    category,
    credibility_score,
    status,
    evidence_origin
)
SELECT
    'What is Redis keyspace notification and when should it be enabled?',
    'Redis keyspace notifications official documentation',
    'Redis Keyspace Notifications',
    'https://redis.io/docs/latest/develop/pubsub/keyspace-notifications/',
    'redis.io',
    'Redis keyspace notifications let clients subscribe to Pub/Sub channels for events affecting keys and values.',
    'Redis keyspace notifications publish events about changes in the Redis data set to Pub/Sub channels. Clients can subscribe to keyspace or keyevent channels to observe operations such as key changes, list pushes, expirations, or evictions. This feature is useful for cache invalidation workflows, expiry-driven processing, monitoring selected key activity, or coordinating lightweight event reactions. It should be enabled selectively because it adds event publishing overhead and may expose high-volume key activity to subscribers.',
    'Redis Keyspace Notifications',
    '## Overview
Redis keyspace notifications allow clients to observe selected events that affect keys and values through Pub/Sub channels. They are useful when an application needs to react to key changes, expirations, or evictions.

## Core Concepts
- Keyspace notifications publish events about Redis data-set changes.
- Clients can subscribe to keyspace or keyevent channels depending on the event view they need.
- Events can include commands affecting a key, keys receiving list operations, expirations, and evictions.

## Configuration
- Keyspace notifications are controlled by the notify-keyspace-events setting.
- Enable only the event classes required by the application.

## Best Practices
- Enable notifications selectively because high-volume keys can create noisy event streams.
- Do not use keyspace notifications as the only durable event pipeline for critical business workflows.
- Monitor subscriber behavior and Redis load after enabling notification classes.',
    'Redis Cache',
    0.96,
    'NEEDS_REVIEW',
    'WEB_SEARCH'
WHERE NOT EXISTS (
    SELECT 1 FROM web_evidence
    WHERE url = 'https://redis.io/docs/latest/develop/pubsub/keyspace-notifications/'
      AND question = 'What is Redis keyspace notification and when should it be enabled?'
);
