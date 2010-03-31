from testfixture import CandlepinTests
import random

class SubscriptionTokenTests(CandlepinTests):

    def setUp(self):
        CandlepinTests.setUp(self)

    def test_subscription_tokens(self):
        result = self.cp.getSubscriptionTokens()
        #self.assertTrue("product" in result)
        subscription_token_list = result
        self.assertNotEquals(0, len(subscription_token_list))
        return subscription_token_list


    def test_delete_subscription_token(self):
        before = self.test_subscription_tokens()
        
        new_token = self.test_create_subscription_token_new()
        self.cp.deleteSubscriptionToken(new_token['subscriptionToken']['id'])
        results = self.test_subscription_tokens()
        self.assertEqual(len(before), len(results))

    def test_create_subscription_token_new(self):
        sub_data =  {'subscription': {'startDate': '2007-07-13T00:00:00-04:00',
                                      'endDate': '2010-07-13T00:00:00-04:00',
                                      'quantity': 37,
                                      'productId': 'provisioning'}}
    
        results = self.cp.createSubscription(sub_data);
        subscription_token_data = {'subscriptionToken': {'token':'foobar-%s' % random.random(),
                                                         'subscription': results['subscription'] }}

        results = self.cp.createSubscriptionToken(subscription_token_data)
        return results


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
