angular.module(PKG.name + '.feature.streams')
  .controller('CdapStreamDetailController', function($scope, MyDataSource, $state, $alert, myStreamService) {
    var dataSrc = new MyDataSource($scope);

    $scope.truncate = function() {
      dataSrc.request({
        _cdapNsPath: '/streams/' + $state.params.streamId + '/truncate',
        method: 'POST'
      }).then(function () {
        $alert({
          type: 'success',
          content: 'Successfully truncated ' + $state.params.streamId + ' stream'
        });
      });
    };

    $scope.sendEvents = function() {
      myStreamService.show($state.params.streamId);
    };

  });
