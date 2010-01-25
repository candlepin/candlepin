// Used in RulesTest.java
function pre_testProduct() {
	print("You called testProduct() with testvar " + testvar + "\n");
	print("type = " + consumer.getType() + "\n");
	print("name = " + consumer.getName() + "\n");
	print("product label = " + product.getLabel() + "\n");
}

// This product's rule is expecting variables that we won't provide them.
function pre_badVariableProduct() {
	print(noSuchVariable.toString());
}

print('Rules parsed! YAY!\n');