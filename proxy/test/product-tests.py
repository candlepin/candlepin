import unittest
import httplib, urllib
import simplejson as json

from testfixture import CandlepinTests
from candlepinapi import Rest, CandlePinApi

class ProductTests(CandlepinTests):

    def test_products(self):
        result = self.cp.getProducts()
        self.assertTrue("product" in result)
        products_list = result['product']
        self.assertEquals(6, len(products_list))
