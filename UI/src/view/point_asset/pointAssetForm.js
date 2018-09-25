(function (root) {
root.PointAssetForm = function() {
  var me = this;
  me.enumeratedPropertyValues = null;
  me.pointAsset = null;
  me.roadCollection = null;
  me.applicationModel = null;
  me.backend= null;
  me.saveCondition= null;

  this.initialize = function(parameters) {
    me.pointAsset = parameters.pointAsset;
    me.roadCollection = parameters.roadCollection;
    me.applicationModel = parameters.applicationModel;
    me.backend = parameters.backend;
    me.saveCondition = parameters.saveCondition;
    me.bindEvents(parameters);
  };

  this.bindEvents = function(parameters) {
    var rootElement = $('#feature-attributes');
    var typeId = parameters.pointAsset.typeId;
    var selectedAsset = parameters.pointAsset.selectedPointAsset;
    var collection  = parameters.pointAsset.collection;
    var layerName = parameters.pointAsset.layerName;
    var localizedTexts = parameters.pointAsset.formLabels;
    var authorizationPolicy = parameters.pointAsset.authorizationPolicy;

    eventbus.on('assetEnumeratedPropertyValues:fetched', function(event) {
      if(event.assetType === typeId)
        me.enumeratedPropertyValues = event.enumeratedPropertyValues;
    });

    parameters.backend.getAssetEnumeratedPropertyValues(typeId);

    eventbus.on('application:readOnly', function(readOnly) {
      if(me.applicationModel.getSelectedLayer() === layerName && (!_.isEmpty(me.roadCollection.getAll()) && !_.isNull(selectedAsset.getId()))){
        me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, me.roadCollection) || readOnly);
      }
    });

    eventbus.on(layerName + ':selected ' + layerName + ':cancelled roadLinks:fetched', function() {
      if (!_.isEmpty(me.roadCollection.getAll()) && !_.isNull(selectedAsset.getId())) {
        me.renderForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, me.roadCollection, collection);
        me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, me.roadCollection) || me.applicationModel.isReadOnly());
        rootElement.find('.form-controls button').prop('disabled', !(selectedAsset.isDirty() && me.saveCondition(selectedAsset)));
        rootElement.find('button#cancel-button').prop('disabled', false);
      }
    });

    eventbus.on(layerName + ':changed', function() {
      rootElement.find('.form-controls button').prop('disabled', !(selectedAsset.isDirty() && me.saveCondition(selectedAsset)));
      rootElement.find('button#cancel-button').prop('disabled', !(selectedAsset.isDirty()));
    });

    eventbus.on(layerName + ':unselected ' + layerName + ':creationCancelled', function() {
      rootElement.empty();
    });

    eventbus.on('layer:selected', function(layer) {
      if (layer === layerName) {
        me.renderLinktoWorkList(layer, localizedTexts);
        if(parameters.pointAsset.hasInaccurate){
          renderInaccurateWorkList(layer);
        }
      }
      else {
        $('#information-content .form[data-layer-name="' + layerName +'"]').remove();
      }
    });
  };

  this.renderForm = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {
    var id = selectedAsset.getId();

    var title = selectedAsset.isNew() ? "Uusi " + localizedTexts.newAssetLabel : 'ID: ' + id;
    var header = '<header><span>' + title + '</span>' + me.renderButtons() + '</header>';
    var form = me.renderAssetFormElements(selectedAsset, localizedTexts, collection);
    var footer = '<footer>' + me.renderButtons() + '</footer>';

    rootElement.html(header + form + footer);

    rootElement.find('input[type="checkbox"]').on('change', function (event) {
      var eventTarget = $(event.currentTarget);
      selectedAsset.set({toBeDeleted: eventTarget.prop('checked')});
    });

    rootElement.find('input[type="text"]').on('input change', function (event) {
      var eventTarget = $(event.currentTarget);
      var obj = {};
      obj[eventTarget.attr('name') ? eventTarget.attr('name') : 'name' ] = eventTarget.val();
      selectedAsset.set(obj);
    });

    rootElement.find('button#change-validity-direction').on('click', function() {
      var previousValidityDirection = selectedAsset.get().validityDirection;
      selectedAsset.set({ validityDirection: validitydirections.switchDirection(previousValidityDirection) });
    });

    rootElement.find('.pointasset button.save').on('click', function() {
      selectedAsset.save();
    });

    rootElement.find('.pointasset button.cancel').on('click', function() {
      selectedAsset.cancel();
    });


    this.boxEvents(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
  };

  this.boxEvents = function (rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection){};

  this.renderAssetFormElements = function(selectedAsset, localizedTexts, collection) {
    var asset = selectedAsset.get();

    if (selectedAsset.isNew()) {
      return '' +
        '<div class="wrapper">' +
        '  <div class="form form-horizontal form-dark form-pointasset">' +
        me.renderValueElement(asset, collection) +
        '  </div>' +
        '</div>';
    } else {
      return '' +
        '<div class="wrapper">' +
        '  <div class="form form-horizontal form-dark form-pointasset">' +
        renderFloatingNotification(asset.floating, localizedTexts) +
        '    <div class="form-group">' +
        '      <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + (asset.createdBy || '-') + ' ' + (asset.createdAt || '') + '</p>' +
        '    </div>' +
        '    <div class="form-group">' +
        '      <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + (asset.modifiedBy || '-') + ' ' + (asset.modifiedAt || '') + '</p>' +
        '    </div>' +
        me.renderValueElement(asset, collection) +
        '    <div class="form-group form-group delete">' +
        '      <div class="checkbox" >' +
        '        <input type="checkbox">' +
        '      </div>' +
        '      <p class="form-control-static">Poista</p>' +
        '    </div>' +
        '  </div>' +
        '</div>';
    }
  };

  this.renderValueElement = function(asset, collection) { return ''; };

  this.renderButtons = function() {
    return '' +
      '<div class="pointasset form-controls">' +
      '  <button id="save-button" class="save btn btn-primary" disabled>Tallenna</button>' +
      '  <button id ="cancel-button" class="cancel btn btn-secondary" disabled>Peruuta</button>' +
      '</div>';
  };

  this.renderLinktoWorkList = function(layerName, localizedTexts) {
    $('#information-content').append('' +
      '<div class="form form-horizontal" data-layer-name="' + layerName + '">' +
      '<a id="point-asset-work-list-link" class="floating-point-assets" href="#work-list/' + layerName + '">Geometrian ulkopuolelle jääneet ' + localizedTexts.manyFloatingAssetsLabel + '</a>' +
      '</div>');
  };

  this.toggleMode = function(rootElement, readOnly) {
    rootElement.find('.delete').toggle(!readOnly);
    rootElement.find('.form-controls').toggle(!readOnly);
    rootElement.find('.editable .form-control-static').toggle(readOnly);
    rootElement.find('.editable .form-control').toggle(!readOnly);
    rootElement.find('.edit-only').toggle(!readOnly);
  };

  var renderFloatingNotification = function(floating, localizedTexts) {
    if (floating) {
      return '' +
        '<div class="form-group form-notification">' +
        ' <p>Kadun tai tien geometria on muuttunut, tarkista ja korjaa ' + localizedTexts.singleFloatingAssetLabel + ' sijainti</p>' +
        '</div>';
    } else {
      return '';
    }
  };

  var renderInaccurateWorkList= function renderInaccurateWorkList(layerName) {
    $('#information-content').append('' +
      '<div class="form form-horizontal" data-layer-name="' + layerName + '">' +
      '<a id="work-list-link-errors" class="wrong-linear-assets" href="#work-list/' + layerName + 'Errors">Laatuvirheet Lista</a>' +
      '</div>');
  };
};
})(this);
