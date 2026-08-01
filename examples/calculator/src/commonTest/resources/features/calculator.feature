Feature: Calculator
  As a shopper
  I want the totals to add up
  So that I am charged the right amount

  Background:
    Given a fresh calculator

  Scenario: adding two numbers
    Given I have entered 5
    And I have entered 7
    When I press add
    Then the result should be 12

  Scenario: multiplying two numbers
    Given I have entered 6
    And I have entered 7
    When I press multiply
    Then the result should be 42

  Scenario Outline: a <percentage>% discount on <total>
    Given I have entered <total>
    And I press add
    When I apply a discount of <percentage> percent
    Then the result should be <expected>

    Examples:
      | total | percentage | expected |
      | 100   | 20         | 80       |
      | 250   | 10         | 225      |
      | 99    | 0          | 99       |

  @slow
  Scenario: summing a basket
    Given the basket contains
      | item   | price |
      | apple  | 1.5   |
      | banana | 2.25  |
      | cherry | 6.25  |
    When I press add
    Then the result should be 10
