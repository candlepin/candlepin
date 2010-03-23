from testfixture import CandlepinTests

class TestTests(CandlepinTests):
    """
    test the TestResource api
    """

    def test_gettest(self):
        path = "/test/gettest"
        result = self.cp.rest.get(path)
        #self.assertTrue("product" in result)
        print result
        self.assertNotEqual(None, result)
        self.assertEqual("myname", result['jsontest']['name'])
        self.assertEquals(3, len(result['jsontest']['stringList']))
        self.assertEquals("parentname", result['jsontest']['parent']['name'])
        self.assertEquals(3, len(result['jsontest']['parent']['stringList']))
