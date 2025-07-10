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
      1: 'Muu pysyvä esterakenne',
      2: 'Avattava puomi',
      3: 'Kaivanne',
      99: 'Ei tiedossa'
    };

    var propertyOrdering = ['suggest_box'];

    this.renderValueElement = function(asset, collection, authorizationPolicy) {
      var components = me.renderComponents(asset.propertyData, propertyOrdering, authorizationPolicy);
      return '' +
        '<div class="edit-mode"> '+
        '    <div class="form-group editable form-obstacle">' +
        '      <label class="control-label required" for="esterakennelma">Esterakennelma</label>' +
        '      <p class="form-control-static">' + obstacleTypes[me.selectedAsset.getByProperty('esterakennelma')] + '</p>' +
        '        <select id="esterakennelma-select" class="form-control" style="display:none">' +
        '         <option value="" ' + (me.selectedAsset.getByProperty('esterakennelma') === null ? 'selected' : '') + '></option>' +
        '         <option value="1" '+ (parseInt(me.selectedAsset.getByProperty('esterakennelma')) === 1 ? 'selected' : '') +'>Muu pysyvä esterakennelma</option>' +
        '         <option value="2" '+ (parseInt(me.selectedAsset.getByProperty('esterakennelma')) === 2 ? 'selected' : '') +'>Avattava puomi</option>' +
        '         <option value="3" '+ (parseInt(me.selectedAsset.getByProperty('esterakennelma')) === 3 ? 'selected' : '') +'>Kaivanne</option>' +
        '         <option value="99" '+ (parseInt(me.selectedAsset.getByProperty('esterakennelma')) === 99 ? 'selected' : '') +'>Ei tiedossa</option>' +
        '      </select>' +
        '    </div>' +
        '</div>' +
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