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
    };

    this.renderValueElement = function(asset) {
      return '' +
        '  <div class="form-group editable form-directional-traffic-sign">' +
        '      <label class="control-label">Teksti</label>' +
        '      <p class="form-control-static">' + (asset.text || '–') + '</p>' +
        '      <textarea class="form-control large-input">' + (asset.text || '') + '</textarea>' +
        '  </div>' +
        '    <div class="form-group editable form-directional-traffic-sign edit-only">' +
        '      <label class="control-label">Vaikutussuunta</label>' +
        '      <button id="change-validity-direction" class="form-control btn btn-secondary btn-block">Vaihda suuntaa</button>' +
        '    </div>';
    };

    this.boxEvents = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {

      rootElement.find('.linear-asset.form textarea, .form-directional-traffic-sign textarea').on('keyup', function (event) {
        var eventTarget = $(event.currentTarget);
        selectedAsset.set({text: eventTarget.val()});
      });
    };
  };
})(this);