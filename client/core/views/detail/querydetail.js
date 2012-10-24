
// Query detail view

define([
	'lib/text!../../partials/querydetail.html'
	], function (Template) {
	return Em.View.extend({
		template: Em.Handlebars.compile(Template),
		didInsertElement: function () {

			$(this.get('element')).css({
				top: '0px',
				right: '-20px'
			});

		},
		injector: Ember.TextField.extend({
			valueBinding: 'parentView.injectValue',
			insertNewline: function() {
				var value = this.get('value');
				if (value) {
					this.get('parentView').inject();
				}
			}
		}),
		classNames: ['popup-modal'],
		__timeout: null,
		controller: Em.Object.create({
			current: null,
			inputs: [],
			outputs: [],
			getStats: function (self) {

				C.get.apply(this.get('current'), this.get('current').getUpdateRequest());

				self.__timeout = setTimeout(function () {
					if (self.get('controller')) {
						self.get('controller').getStats(self);
					}
				}, 1000);

			}
		}),
		show: function (current) {

			var self = this;
			var controller = this.get('controller');

			controller.set('current', current);
			controller.getStats(self);
			$(this.get('element')).show();

		},
		close: function () {

			this.set('current', null);
			$(this.get('element')).hide();
			clearTimeout(this.__timeout);

		},
		injectValue: null,
		inject: function () {
			
			var payload = this.get('injectValue');
			var flow = C.Ctl.Flow.current.get('id');
			var stream = '';
			if (C.Ctl.Flow.current.get('flowStreams')) {
				stream = C.Ctl.Flow.current.get('flowStreams')[0].name;
			}

			this.set('injectValue', '');

			C.get('gateway', {
				method: 'inject',
				params: {
					name: flow,
					stream: stream,
					payload: payload
				}
			}, function (error, response) {

				if (error) {
					C.Vw.Modal.show(
					"Inject Error",
					"The gateway responded with: " + error.statusCode + ': ' + error.data, function () {
						window.location.reload();
					});
				}

			});
		}
	});
});