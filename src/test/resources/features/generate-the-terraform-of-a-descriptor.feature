Feature: The Terraform of the infrastructure of a descriptor is generated for a target

  The generator knows no technology: every piece of the infrastructure asks the plugin of its type for its Terraform
  in the chosen target, local or aws, and the generator joins what they give (ADR-024). In aws every module gets a
  role that reaches only the pieces it uses. Nothing is applied: the files are written for someone to review.

  The scenarios use plugins that exist only in the tests:
  - TEST_STORE gives one resource per piece, named after the piece, and a host it shares with every other
    TEST_STORE piece.
  - TEST_IN_PROCESS gives nothing, in any target: what it builds lives in the process.
  - TEST_LOCAL_ONLY gives a resource locally and cannot be generated for aws.

  @DEV-98
  Scenario: Every piece adds what its plugin gives for the target
    Given the descriptor:
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: TEST_IN_PROCESS}
        inbox: {kind: INBOX, type: TEST_STORE, configuration: {name: orders-inbox}}
        lake: {kind: DATA_LAKE, type: TEST_LOCAL_ONLY, configuration: {name: orders-lake}}
      modules: []
      """
    When the Terraform of the descriptor is generated for the target "local"
    Then the Terraform has the resource "test_store" "inbox"
    And the Terraform has the resource "test_local_only" "lake"
    And the Terraform has nothing for the piece "bus"

  @DEV-98
  Scenario: A resource that several pieces share is generated once
    Given the descriptor:
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: TEST_IN_PROCESS}
        inbox: {kind: INBOX, type: TEST_STORE, configuration: {name: orders-inbox}}
        lake: {kind: DATA_LAKE, type: TEST_STORE, configuration: {name: orders-lake}}
      modules: []
      """
    When the Terraform of the descriptor is generated for the target "local"
    Then the Terraform has the resources "inbox" and "lake" of "test_store"
    And the Terraform has the resource "test_store_host" "shared" once

  @DEV-98
  Scenario: A type that cannot be generated for the target fails with the piece and the type
    Given the descriptor:
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: TEST_IN_PROCESS}
        lake: {kind: DATA_LAKE, type: TEST_LOCAL_ONLY, configuration: {name: orders-lake}}
      modules: []
      """
    When the Terraform of the descriptor is generated for the target "aws"
    Then the generation fails
    And the error names "infrastructure.lake: type TEST_LOCAL_ONLY cannot be generated for the target aws"

  @DEV-98
  Scenario: A type with no plugin that generates its Terraform fails with the piece and the type
    Given the descriptor:
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: TEST_IN_PROCESS}
        inbox: {kind: INBOX, type: NOBODY_GENERATES_THIS, configuration: {}}
      modules: []
      """
    When the Terraform of the descriptor is generated for the target "local"
    Then the generation fails
    And the error names "infrastructure.inbox: type NOBODY_GENERATES_THIS has no plugin that generates its Terraform"

  @DEV-98
  Scenario: A value from the environment becomes a Terraform variable and never reaches the files
    Given the descriptor:
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: TEST_IN_PROCESS}
        inbox: {kind: INBOX, type: TEST_STORE, configuration: {name: "${env:INBOX_NAME}"}}
      modules: []
      """
    And the environment variable "INBOX_NAME" is "secret-inbox-name"
    When the Terraform of the descriptor is generated for the target "local"
    Then the Terraform declares the variable "INBOX_NAME"
    And no generated file contains "secret-inbox-name"

  @DEV-98
  Scenario: In aws every module gets a role that reaches only the pieces it uses
    Given the descriptor:
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: TEST_IN_PROCESS}
        inbox: {kind: INBOX, type: TEST_STORE, configuration: {name: orders-inbox}}
        lake: {kind: DATA_LAKE, type: TEST_STORE, configuration: {name: orders-lake}}
      modules:
        - crawler:
            type: POLL
            id: orders-crawler
            source: {type: HTTP, normalizer: JSON_OBJECT, mediaType: JSON, configuration: {sourceId: orders, url: "http://localhost/orders"}}
            eventMapping: {namingConvention: SNAKE_CASE}
        - ingester:
            type: REACTIVE
            id: orders-ingester
      """
    When the Terraform of the descriptor is generated for the target "aws"
    Then the role of "orders-crawler" reaches the piece "inbox" and nothing else
    And the role of "orders-ingester" reaches the pieces "inbox" and "lake" and nothing else
