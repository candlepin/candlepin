from testfixture import CandlepinTests

class ProductTests(CandlepinTests):

    def test_products(self):
        result = self.cp.getProducts()
        #self.assertTrue("product" in result)
        products_list = result
        self.assertEquals(6, len(products_list))
