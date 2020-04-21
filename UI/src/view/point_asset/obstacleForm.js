(function(root) {
  root.ObstacleForm = function() {
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

    var obstacleTypes = {
      1: 'Suljettu yhteys',
      2: 'Avattava puomi'
    };

    //TODO: investigate obstacle to be created by point asset form
    var propertyOrdering = ['suggest_box'];

    this.renderValueElement = function(asset, collection, authorizationPolicy) {
      var components = me.renderComponents(asset, propertyOrdering, authorizationPolicy);
      return '' +
        '    <div class="form-group editable form-obstacle">' +
        '      <label class="control-label">Esterakennelma</label>' +
        '      <p class="form-control-static">' + obstacleTypes[me.selectedAsset.getByProperty('esterakennelma')] + '</p>' +
        '      <select class="form-control" style="display:none">  ' +
        '        <option value="1" '+ (parseInt(me.selectedAsset.getByProperty('esterakennelma')) === 1 ? 'selected' : '') +'>Suljettu yhteys</option>' +
        '        <option value="2" '+ (parseInt(me.selectedAsset.getByProperty('esterakennelma')) === 2 ? 'selected' : '') +'>Avattava puomi</option>' +
        '      </select>' +
        '    </div>' +
          components;
    };

    this.boxEvents = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {

      rootElement.find('.form-obstacle select').on('change', function(event) {
        var eventTarget = $(event.currentTarget);
        selectedAsset.setPropertyByPublicId('esterakennelma', parseInt(eventTarget.val(), 10));
      });
    };
  };
})(this);