angular.module(PKG.name + '.commons')
  .factory('myExplorerApi', function($state, myCdapUrl, $resource, myAuth, MY_CONFIG) {
    var url = myCdapUrl.constructUrl,
        basepath = '/data/explore/queries';

    return $resource(
      url({ _cdapNsPath: basepath }),
      {},
      {
        save: {
          method: 'POST',
          options: { type: 'REQUEST' }
        },
        get: {
          isArray: true,
          options: { type: 'REQUEST' }
        },
        execute: {
          method: 'POST',
          options: { type: 'REQUEST' }
        },
        fetchResults: {
          isArray: true,
          options: { type: 'REQUEST' },
          url : url({ _cdapPath: basepath + '/:queryHandle/schema' }),
          params: {
            queryHandle: '@queryHandle'
          }
        },
        queryPreview: {
          isArray: true,
          method: 'POST',
          options: { type: 'REQUEST' },
          url: url({ _cdapPath: basepath + '/:queryHandle/preview' }),
          params: {
            queryHandle: '@queryHandle'
          }
        }
      }
    );
  });
