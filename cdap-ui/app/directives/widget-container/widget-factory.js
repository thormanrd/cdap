angular.module(PKG.name + '.commons')
  .service('WidgetFactory', function() {
    this.registry = {
      'textbox': {
        element: '<input/>',
        attributes: {
          'class': 'form-control',
          'ng-model': 'model',
          placeholder: 'myconfig.description'
        }
      },
      'password': {
        element: '<input/>',
        attributes: {
          'class': 'form-control',
          'ng-model': 'model',
          type: 'password',
          placeholder: 'myconfig.description'
        }
      },
      'json-editor': {
        element: '<textarea></textarea>',
        attributes: {
          'cask-json-edit': 'model',
          'class': 'form-control',
          placeholder: 'myconfig.description'
        }
      },
      'javascript-editor': {
        element: '<div my-ace-editor></div>',
        attributes: {
          'ng-model': 'model'
        }
      }
    };
    this.registry['__default__'] = this.registry['textbox'];
  });