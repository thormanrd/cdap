angular.module(PKG.name + '.feature.adapters')
  .config(function($stateProvider, $urlRouterProvider, MYAUTH_ROLE) {
    $stateProvider
      .state('adapters', {
        url: '/adapters',
        abstract: true,
        parent: 'apps',
        data: {
          authorizedRoles: MYAUTH_ROLE.all,
          highlightTab: 'development'
        },
        template: '<ui-view/>'
      })

        .state('adapters.list', {
          url: '',
          templateUrl: '/assets/features/adapters/templates/list.html',
          controller: 'AdapterListController',
          ncyBreadcrumb: {
            label: 'Adapters',
            parent: 'overview'
          }
        })

        .state('adapters.create', {
          url: '/create',
          params: {
            data: null
          },
          templateUrl: '/assets/features/adapters/templates/create.html',
          controller: 'AdapterCreateController',
          ncyBreadcrumb: {
            skip: true
          }
        })

        .state('adapters.detail', {
          url: '/:adapterId',
          data: {
            authorizedRoles: MYAUTH_ROLE.all,
            highlightTab: 'development'
          },
          resolve : {
            rRuns: function(MyDataSource, $stateParams, $q) {
              var defer = $q.defer();
              var dataSrc = new MyDataSource();
              // Using _cdapPath here as $state.params is not updated with
              // runid param when the request goes out
              // (timing issue with re-direct from login state).
              dataSrc.request({
                _cdapPath: '/namespaces/' + $stateParams.namespace +
                           '/adapters/' + $stateParams.adapterId +
                           '/runs'
              })
                .then(function(res) {
                  defer.resolve(res);
                });
              return defer.promise;
            }
          },
          ncyBreadcrumb: {
            parent: 'adapters.list',
            label: '{{$state.params.adapterId | caskCapitalizeFilter}}'
          },
          templateUrl: '/assets/features/adapters/templates/detail.html',
          controller: 'AdpaterDetailController'
        })
          .state('adapters.detail.runs',{
            url: '/runs',
            templateUrl: '/assets/features/adapters/templates/tabs/runs.html',
            controller: 'AdapterRunsController',
            ncyBreadcrumb: {
              parent: 'adapters.list',
              label: '{{$state.params.adapterId | caskCapitalizeFilter}}'
            }
          })
            .state('adapters.detail.runs.run', {
              url: '/:runid',
              templateUrl: '/assets/features/adapters/templates/tabs/runs/run-detail.html',
              ncyBreadcrumb: {
                label: '{{$state.params.runid}}'
              }
            })

        .state('adapters.detail.datasets', {
          url: '/datasets',
          data: {
            authorizedRoles: MYAUTH_ROLE.all,
            highlightTab: 'development'
          },
          templateUrl: 'data-list/data-list.html',
          controller: 'AdapterDatasetsController',
          ncyBreadcrumb: {
            label: 'History'
          }
        })
        .state('adapters.detail.history', {
          url: '/history',
          data: {
            authorizedRoles: MYAUTH_ROLE.all,
            highlightTab: 'development'
          },
          templateUrl: '/assets/features/adapters/templates/tabs/history.html',
          controller: 'AdapterRunsController',
          ncyBreadcrumb: {
            label: 'History'
          }
        });
  });
