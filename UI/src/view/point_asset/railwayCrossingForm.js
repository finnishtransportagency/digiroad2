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

    var namePublicId = 'rautatien_tasoristeyksen_nimi';
    var safetyEquipmentPublicId = 'turvavarustus';
    var codePublicId = 'tasoristeystunnus';

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
          '        <p class="form-control-static">' + (me.getPointPropertyValue(asset, codePublicId) || '–') + '</p>' +
          '        <input type="text" class="form-control"  maxlength="15" name="tasoristeystunnus" value="' + (me.getPointPropertyValue(asset, codePublicId) || '')  + '">' +
          '    </div>' +
          '    <div class="form-group editable form-railway-crossing">' +
          '      <label class="control-label">Turvavarustus</label>' +
          '      <p class="form-control-static">' + safetyEquipments[parseInt(me.getPointPropertyValue(asset,'turvavarustus'))] + '</p>' +
          '      <select class="form-control" style="display:none">  ' +
          '        <option value="1" '+ (parseInt(me.getPointPropertyValue(asset, safetyEquipmentPublicId)) === 1 ? 'selected' : '') +'>Rautatie ei käytössä</option>' +
          '        <option value="2" '+ (parseInt(me.getPointPropertyValue(asset, safetyEquipmentPublicId)) === 2 ? 'selected' : '') +'>Ei turvalaitetta</option>' +
          '        <option value="3" '+ (parseInt(me.getPointPropertyValue(asset, safetyEquipmentPublicId)) === 3 ? 'selected' : '') +'>Valo/äänimerkki</option>' +
          '        <option value="4" '+ (parseInt(me.getPointPropertyValue(asset, safetyEquipmentPublicId)) === 4 ? 'selected' : '') +'>Puolipuomi</option>' +
          '        <option value="5" '+ (parseInt(me.getPointPropertyValue(asset, safetyEquipmentPublicId)) === 5 ? 'selected' : '') +'>Kokopuomi</option>' +
          '      </select>' +
          '    </div>' +
          '    <div class="form-group editable form-railway-crossing">' +
          '        <label class="control-label">' + 'Nimi' + '</label>' +
          '        <p class="form-control-static">' + (me.getPointPropertyValue(asset, namePublicId) || '–') + '</p>' +
          '        <input type="text" class="form-control" name="rautatien_tasoristeyksen_nimi" value="' + (me.getPointPropertyValue(asset, namePublicId) || '')  + '">' +
          '    </div>';
    };

    this.boxEvents = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {

      rootElement.find('.form-railway-crossing select').on('change', function(event) {
        var eventTarget = $(event.currentTarget);
        selectedAsset.setPropertyByPublicId(safetyEquipmentPublicId, parseInt(eventTarget.val(), 10));
      });

    };
  };
})(this);