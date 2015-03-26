'use strict';

bikeManApp.factory('Transaction', ['$resource',
    function ($resource) {
        return $resource('api/transactions/:id', {}, {
            'get': { method: 'GET'},

            'query': { method: 'GET', isArray: true},
            'queryOpenTransactions':{ method: 'GET', isArray: true, url: 'api/transactions/open'},
            'queryClosedTransactions':{ method: 'GET', isArray: true, url: 'api/transactions/closed'},
            'queryTransactionsOfPedelecWithSize' : { method: 'GET', isArray: true, url: "api/transactions/pedelec/:pedelecId"},
            'queryTransactionsOfCustomerWithSize': { method: 'GET', isArray: true, url: "api/transactions/customer/:login"},

            'queryMajorCustomerTransactions': {method: 'GET', isArray: true, url: 'api/major-customer/transactions'},
            'queryOpenMajorCustomerTransactions': {method: 'GET', isArray: true, url: 'api/major-customer/transactions/open'},
            'queryClosedMajorCustomerTransactions': {method: 'GET', isArray: true, url: 'api/major-customer/transactions/closed'},
            'queryMajorCustomerTransactionsOfPedelecWithSize': {method: 'GET', isArray: true, url: 'api/major-customer/transactions/pedelec/:pedelecId'},
            'queryMajorCustomerTransactionsOfLoginWithSize': {method: 'GET', isArray: true, url: 'api/major-customer/transactions/customer/:login'}
        });
    }]);
