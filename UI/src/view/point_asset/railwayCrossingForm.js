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
      me.selectedAsset = parameters.pointAsset.selectedPointAsset;
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

    var propertyOrdering = ['suggest_box'];

    this.renderValueElement = function(asset, collection, authorizationPolicy) {
      var components = me.renderComponents(asset.propertyData, propertyOrdering, authorizationPolicy);
      var safetyEquipmentValue = me.selectedAsset.getByProperty(safetyEquipmentPublicId);
        return '' +
          '    <div class="form-group editable form-railway-crossing">' +
          '        <label class="control-label">Tasoristeystunnus</label>' +
          '        <p class="form-control-static">' + ( me.selectedAsset.getByProperty(codePublicId) || '–') + '</p>' +
          '        <input type="text" class="form-control"  maxlength="15" name="tasoristeystunnus" id="tasoristeystunnus" value="' + ( me.selectedAsset.getByProperty(codePublicId) || '')  + '">' +
          '    </div>' +
          '    <div class="form-group editable form-railway-crossing">' +
          '      <label class="control-label">Turvavarustus</label>' +
          '      <p class="form-control-static">' + safetyEquipments[parseInt( me.selectedAsset.getByProperty('turvavarustus'))] + '</p>' +
          '      <select class="form-control" style="display:none">  ' +
          '        <option value="1" '+ (parseInt(safetyEquipmentValue) === 1 ? 'selected' : '') +'>Rautatie ei käytössä</option>' +
          '        <option value="2" '+ (parseInt(safetyEquipmentValue) === 2 ? 'selected' : '') +'>Ei turvalaitetta</option>' +
          '        <option value="3" '+ (parseInt(safetyEquipmentValue) === 3 ? 'selected' : '') +'>Valo/äänimerkki</option>' +
          '        <option value="4" '+ (parseInt(safetyEquipmentValue) === 4 ? 'selected' : '') +'>Puolipuomi</option>' +
          '        <option value="5" '+ (parseInt(safetyEquipmentValue) === 5 ? 'selected' : '') +'>Kokopuomi</option>' +
          '      </select>' +
          '    </div>' +
          '    <div class="form-group editable form-railway-crossing">' +
          '        <label class="control-label">Nimi</label>' +
          '        <p class="form-control-static">' + ( me.selectedAsset.getByProperty(namePublicId) || '–') + '</p>' +
          '        <input type="text" class="form-control" name="rautatien_tasoristeyksen_nimi" id="rautatien_tasoristeyksen_nimi" value="' + ( me.selectedAsset.getByProperty(namePublicId) || '')  + '">' +
          '    </div>' +
            components;
    };

    this.boxEvents = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {

      rootElement.find('.form-railway-crossing select').on('change', function(event) {
        var eventTarget = $(event.currentTarget);
        selectedAsset.setPropertyByPublicId(safetyEquipmentPublicId, parseInt(eventTarget.val(), 10));
      });

      rootElement.find('.form-railway-crossing input[type=text]').on('change input', function (event) {
        var eventTarget = $(event.currentTarget);
        var propertyPublicId = eventTarget.attr('id');
        var propertyValue = eventTarget.val();
        selectedAsset.setPropertyByPublicId(propertyPublicId, propertyValue);
      });
    };
  };
})(this);