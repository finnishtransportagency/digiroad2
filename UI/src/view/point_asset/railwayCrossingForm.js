(function(root) {
  root.RailwayCrossingForm = function() {
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

    var safetyEquipments = {
      1: 'Rautatie ei käytössä',
      2: 'Ei turvalaitetta',
      3: 'Valo/äänimerkki',
      4: 'Puolipuomi',
      5: 'Kokopuomi'
    };

    this.renderValueElement = function(asset) {
        return '' +
          '    <div class="form-group editable form-railway-crossing">' +
          '        <label class="control-label">' + 'Tasoristeystunnus' + '</label>' +
          '        <p class="form-control-static">' + (asset.code || '–') + '</p>' +
          '        <input type="text" class="form-control"  maxlength="15" name="code" value="' + (asset.code || '')  + '">' +
          '    </div>' +
          '    <div class="form-group editable form-railway-crossing">' +
          '      <label class="control-label">Turvavarustus</label>' +
          '      <p class="form-control-static">' + safetyEquipments[asset.safetyEquipment] + '</p>' +
          '      <select class="form-control" style="display:none">  ' +
          '        <option value="1" '+ (asset.safetyEquipment === 1 ? 'selected' : '') +'>Rautatie ei käytössä</option>' +
          '        <option value="2" '+ (asset.safetyEquipment === 2 ? 'selected' : '') +'>Ei turvalaitetta</option>' +
          '        <option value="3" '+ (asset.safetyEquipment === 3 ? 'selected' : '') +'>Valo/äänimerkki</option>' +
          '        <option value="4" '+ (asset.safetyEquipment === 4 ? 'selected' : '') +'>Puolipuomi</option>' +
          '        <option value="5" '+ (asset.safetyEquipment === 5 ? 'selected' : '') +'>Kokopuomi</option>' +
          '      </select>' +
          '    </div>' +
          '    <div class="form-group editable form-railway-crossing">' +
          '        <label class="control-label">' + 'Nimi' + '</label>' +
          '        <p class="form-control-static">' + (asset.name || '–') + '</p>' +
          '        <input type="text" class="form-control" value="' + (asset.name || '')  + '">' +
          '    </div>';
    };

    this.boxEvents = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {

      rootElement.find('.form-railway-crossing select').on('change', function(event) {
        var eventTarget = $(event.currentTarget);
        selectedAsset.set({ safetyEquipment: parseInt(eventTarget.val(), 10) });
      });

    };
  };
})(this);