angular.module(PKG.name + '.commons')
  .directive('loadingIcon', function(myLoadingService, $bootstrapModal, $timeout, EventPipe, $state, $alert) {
    return {
      restrict: 'EA',
      scope: true,
      template: '<div></div>',
      controller: function($scope) {
        var modalObj = {
          templateUrl: 'app-level-loading-icon/loading.html',
          backdrop: 'static',
          keyboard: true,
          scope: $scope,
          windowClass: 'custom-loading-modal'
        }, modal, isBackendDown = false;

        EventPipe.on('backendDown', function(message) {
          if (!isBackendDown) {
            modal && modal.close();
            isBackendDown = true;
            if (!message) {
              $scope.message = 'Service(s) are offline';
            } else {
              $scope.message = message;
            }
            modal = $bootstrapModal.open(modalObj);
            modal.result.finally(function() {
              $state.go('overview', {}, {reload: true});
            });
          }
        }.bind($scope));

        EventPipe.on('backendUp', function(message) {
          if (isBackendDown) {
            modal.close();
            modal = null;
            isBackendDown = false;

            $alert({
              type: 'success',
              content: message ? message : 'Services are online'
            });
          }
        }.bind($scope));

        EventPipe.on('hideLoadingIcon', function() {
          // Just making it smooth instead of being too 'speedy'
          $timeout(function() {
            if (!isBackendDown) {
              modal && !modal.$state && modal.close();
              modal = null;
            }
          }, 2000);
        });

        EventPipe.on('showLoadingIcon', function() {
          if(!modal && !isBackendDown) {
            $scope.message = '';
            modal = $bootstrapModal.open(modalObj);
          }
        }.bind($scope));
      }
    };
  });
