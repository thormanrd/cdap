angular.module(PKG.name + '.feature.streams')
  .controller('CdapStreamExploreController',
    function($scope, MyDataSource, $state, myHelpers, $log, myExplorerApi) {

      var dataSrc = new MyDataSource($scope);
      var params = {
        scope: $scope
      };
      $scope.activePanel = 0;


      var now = Date.now();

      $scope.eventSearch = {
        startMs: now-(60*60*1000*2), // two hours ago
        endMs: now,
        limit: 10,
        results: []
      };

      $scope.doEventSearch = function () {
        dataSrc
          .request({
            _cdapNsPath: '/streams/' + $state.params.streamId +
              '/events?start=' + $scope.eventSearch.startMs +
              '&end=' + $scope.eventSearch.endMs +
              '&limit=' + $scope.eventSearch.limit
          }, function (result) {
            $scope.eventSearch.results = result;
          });
      };


      $scope.doEventSearch();


      $scope.query = 'SELECT * FROM dataset_history LIMIT 5';

      $scope.execute = function() {
        var prom = myExplorerApi.execute(params, {
          'body': {
            query: $scope.query
          }
        });
        prom.$promise.then(function(res) {
            console.log("Executed");
            $scope.getQueries();
            $scope.activePanel = 2;
        });
      };

      $scope.queries = [];

      $scope.getQueries = function() {
        console.log("Get Queries");
        myExplorerApi.get(params)
          .$promise.then(function(queries) {
            $scope.queries = queries;
          });
      };

      $scope.getQueries();

      $scope.responses = {};

      $scope.fetchResult = function(query) {
        $scope.responses.request = query;
        // request schema
        console.log("Get Schema");
        myExplorerApi.fetchResults(angular.extend({queryHandle: query.query_handle}, params))
          .$promise.then(function(result) {
            console.log("Get Schema Success!");
            $scope.responses.schema = result;
          })

        console.log("Get Preview");
        // request preview
        myExplorerApi.queryPreview(angular.extend({queryHandle: query.query_handle}, params), {})
          .$promise.then(function(result) {
            console.log("Get Preview Success!");
            $scope.responses.results = result;
          })
      };

      $scope.download = function(query) {

        dataSrc
          .request({
            _cdapPath: '/data/explore/queries/' +
                            query.query_handle + '/download',
            method: 'POST'
          })
          .then(function (res) {
            var element = angular.element('<a/>');
            element.attr({
              href: 'data:atachment/csv,' + encodeURIComponent(res),
              target: '_self',
              download: 'result.csv'
            })[0].click();
          });
      };


    }
  );
