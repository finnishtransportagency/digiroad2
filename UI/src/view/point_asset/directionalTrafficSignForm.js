(function(root) {
  root.DirectionalTrafficSignForm = function() {
    PointAssetForm.call(this);
    var me = this;

    this.initialize = function(parameters) {
      me.pointAsset = parameters.pointAsset;
      me.roadCollection = parameters.roadCollection;
      me.applicationModel = parameters.applicationModel;
      me.backend = parameters.backend;
      me.saveCondition = parameters.saveCondition;
      me.feedbackCollection = parameters.feedbackCollection;
      me.bindEvents(parameters);
      me.selectedAsset = parameters.pointAsset.selectedPointAsset;
    };

    //TODO: investigate DirectionalTrafficSign to be created by point asset form
    var propertyOrdering = ['suggest_box'];

    this.renderValueElement = function(asset, collection, authorizationPolicy) {
      var components = me.renderComponents(asset, propertyOrdering, authorizationPolicy);
      return '' +
        '  <div class="form-group editable form-directional-traffic-sign">' +
        '      <label class="control-label">Teksti</label>' +
        '      <p class="form-control-static">' + (me.selectedAsset.getByProperty('opastustaulun_teksti') || '–') + '</p>' +
        '      <textarea class="form-control large-input">' + (me.selectedAsset.getByProperty('opastustaulun_teksti') || '') + '</textarea>' +
        '  </div>' +
          me.renderValidityDirection(asset) +
          components;
    };

    this.boxEvents = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {

      rootElement.find('.linear-asset.form textarea, .form-directional-traffic-sign textarea').on('keyup', function (event) {
        var eventTarget = $(event.currentTarget);
        selectedAsset.setPropertyByPublicId("opastustaulun_teksti", eventTarget.val());
      });
    };
  };
})(this);