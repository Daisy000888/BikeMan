'use strict';

bikeManApp
    .config(['$stateProvider', '$httpProvider', '$translateProvider', 'USER_ROLES',
        function ($stateProvider, $httpProvider, $translateProvider, USER_ROLES) {
            $stateProvider
                .state('majorcustomers', {
                    url: '/majorcustomers',
                    templateUrl: 'views/majorcustomers.html',
                    controller: 'MajorcustomerController',
                    resolve:{
                        resolvedMajorcustomers: ['Majorcustomer', function (Majorcustomer) {
                            return Majorcustomer.query();
                        }]
                    },
                    access: {
                        authorizedRoles: [USER_ROLES.all]
                    }
                })
                .state('majorcustomerCreate', {
                    url: '/majorcustomers/create/',
                    templateUrl: 'views/majorcustomerCreate.html',
                    controller: 'MajorcustomerCreateController',
                    access: {
                        authorizedRoles: [USER_ROLES.all]
                    }
                })
        }]);
