angular.module(PKG.name + '.feature.admin').controller('AdminServiceDetailController',
function ($scope, $state, MyDataSource) {
    $scope.basePath = '/system/services/' + $state.params.serviceName;
    var myDataSrc = new MyDataSource($scope);

    myDataSrc.request({
      _cdapPath: $scope.basePath  + '/instances'
    })
      .then(function(response) {
        $scope.instances = response;
      });

    $scope.tabs = [];
    if ($state.is('admin.system.services.detail')) {
      $scope.tabs.activeTab = 0;
    } else {
      $scope.tabs.activeTab = ($state.is('admin.system.services.detail.metadata')? 0: 1);
    }
    $scope.$watch('tabs.activeTab', function(newValue) {
      if(newValue === 0) {
        $state.go('admin.system.services.detail.metadata');
      } else if(newValue === 1) {
        $state.go('admin.system.services.detail.logs');
      }
    });
});
