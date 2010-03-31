Feature: Upload Rules
	In order to enforce rules for entitlements
	As a Candlepin
	I want to be able to upload rules and get the current rule set

   	Scenario: Rules are Downloaded
		Given I am a Consumer Somebox
		When I Download Rules
		Then I Have Rules

      Scenario: Rules are Uploaded
       		 Given I am a Consumer Somebox
		 And I Download Original Rules
		 And I have ruleset "ffffffffffffffffffffffff"
		 When I Upload Ruleset
		 Then I Have Rules
		 And I Upload Original Rules
#       	 	When I Consume an Entitlement for the "virtualization_host" Product
#        	Then I Have 1 Entitlement
