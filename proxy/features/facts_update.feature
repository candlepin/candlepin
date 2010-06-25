Feature: Update a Consumer's Facts
    In order to reflect my current hardware profile, so that I can obtain the correct software for my system
    As a Consumer
    I want to be able to update my facts

    Background:
      Given an owner admin "bob"
      And I am logged in as "bob"

    Scenario:  Single fact updated
      Given I register a consumer "mymachine" with the following facts:
          | Name             | Value   |
          | cpu.architecture | i686    |
          | memory.memtotal  | 2004036 | 
      When I update my facts to:
          | Name             | Value   |
          | cpu.architecture | i686    |
          | memory.memtotal  | 4008072 | 
      Then my fact "memory.memtotal" is "4008072"

    Scenario:  Fact is added
      Given I register a consumer "box" with the following facts:
          | Name             | Value   |
          | cpu.architecture | i686    |
          | memory.memtotal  | 2004036 |
      When I update my facts to:
          | Name             | Value   |
          | cpu.architecture | i686    |
          | memory.memtotal  | 2004036 |
          | uname.sysname    | Linux   | 
      Then my fact "uname.sysname" is "Linux"

    Scenario:  Fact is removed
      Given I register a consumer "consumer" with the following facts:
          | Name             | Value   |
          | cpu.architecture | i686    |
          | memory.memtotal  | 2004036 |
          | uname.sysname    | Linux   |
      When I update my facts to:
          | Name             | Value   |
          | memory.memtotal  | 2004036 |
          | uname.sysname    | Linux   |
      Then I have no fact "cpu.architecture"
