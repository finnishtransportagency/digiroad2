(function(root) {
  root.HeightLimitForm = function() {
    PointAssetForm.call(this);
    var me = this;

    this.initialize = function(parameters) {
      me.pointAsset = parameters.pointAsset;
      me.roadCollection = parameters.roadCollection;
      me.applicationModel = parameters.applicationModel;
      me.backend = parameters.backend;
      me.saveCondition = parameters.saveCondition;
      me.bindEvents(parameters);
    };


    this.renderValueElement = function(asset) {
      if(asset.limit || asset.limit === 0){
        return '' +
          '  <div class="form-group editable form-heigh">' +
          '      <label class="control-label">Rajoitus</label>' +
          '      <p class="form-control-static">' + (asset.limit ? (asset.limit + ' cm') : '–') + '</p>' +
          '  </div>';
      } else {
        return '';
      }
    };

    this.renderLinktoWorkList = function(layerName, localizedTexts) {};

  };
})(this);