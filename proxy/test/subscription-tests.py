from testfixture import CandlepinTests

import pprint

class SubscriptionTests(CandlepinTests):


    def setUp(self):
        CandlepinTests.setUp(self)

    def test_subscriptions(self):
        result = self.cp.getSubscriptions()
   #     pprint.pprint(result)
        #self.assertTrue("product" in result)
        subscription_list = result
        #        print result
        self.assertNotEquals(0, len(subscription_list))
#        self.assertEquals(6, len(subscription_list))

    def test_delete_subscription(self):
        
        sub_data =  {'subscription': {'startDate': '2007-07-13T00:00:00-04:00',
                                      'endDate': '2010-07-13T00:00:00-04:00',
                                      'quantity': 37,
                                      'productId': 'provisioning'}}
    
        results = self.cp.createSubscription(sub_data);
        print results
        results = self.cp.deleteSubscription(results['subscription']['id'])

    def test_create_subscription_new(self):
        sub_data =  {'subscription': {'startDate': '2007-07-13T00:00:00-04:00',
                                      'endDate': '2010-07-13T00:00:00-04:00',
                                      'quantity': 37,
                                      'productId': 'provisioning'}}
    
        results = self.cp.createSubscription(sub_data);

    def test_create_subscription(self):
        sub_data =  {'startDate': '2007-07-13T00:00:00-04:00',
                     'endDate': '2010-07-13T00:00:00-04:00',
                     'activeSubscription': True,
                     'consumed': 0,
                     'subscriptionId': 3,
                     'quantity': 20000,
                     'id': 3,
                     'productId': 'provisioning'}

        result = self.cp.getSubscriptions()
        sub = result[0]
        del sub['subscription']['id']
#        del sub['subscription']['quantity']
        results = self.cp.createSubscription(sub);
