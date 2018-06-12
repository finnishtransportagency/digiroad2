(function(root) {
  root.LinearAssetMassUpdateDialog = {
    show: init
  };

  function init(options) {
    var count = options.count,
      onCancel = options.onCancel,
      onSave = options.onSave,
      validator = options.validator,
      formElements = options.formElements,
      selectedLinearAsset = options.selectedLinearAsset,
      assetTypeConfiguration = options.assetTypeConfiguration,
      currentValue;

    var confirmDiv =
      '<div class="modal-overlay mass-update-modal">' +
        '<div class="modal-dialog form form-horizontal linear-asset">' +
          '<div class="content">' +
            'Olet valinnut <%- count %> tielinkkiä' +
          '</div>' +
          '<div class="form-elements-container">' +
            '<%= editElement %>'+
          '</div>'+
          '<div class="actions">' +
            '<button class="btn btn-primary save">Tallenna</button>' +
            '<button class="btn btn-secondary close">Peruuta</button>' +
          '</div>' +
        '</div>' +
      '</div>';

    function setValue(value) {
      if (validator(value)) {
        currentValue = value;
        selectedLinearAsset.setMultiValue(currentValue);
        $('button.save').prop('disabled', '');
      } else {
        $('button.save').prop('disabled', 'disabled');
      }
    }

    function _setValue(value){
      if (validator(value)) {

        if(!currentValue)
          currentValue = value;

        else{
          var properties = _.find(currentValue.properties, function(property){ return property.publicId === value.properties[0].publicId; });
          if (properties) 
            properties.values = value.properties[0].values;

          else{
            currentValue.properties.push({
              publicId: value.properties[0].publicId,
              values: value.properties[0].values,
              propertyType: value.properties[0].propertyType,
              required: value.properties[0].required
            });
          }
        }

        if(requiredPropertiesMissing())
          $('button.save').prop('disabled', '');
        else
          $('button.save').prop('disabled', 'disabled');
      }

      else {
        $('button.save').prop('disabled', 'disabled');
      }
    }

    function requiredPropertiesMissing() {

      return _.every(currentValue.properties, function(property){
        if(!property.required)
          return true;

        if(_.isEmpty(property.values))
          return false;

        return _.some(property.values, function(value){ return value && !_.isEmpty(value.value); });
      });

    }

    function removeValue() {
      currentValue = undefined;
      selectedLinearAsset.removeMultiValue();
      $('button.save').prop('disabled', '');
    }

    var renderDialog = function() {
      var container = $('.container').append(_.template(confirmDiv)({
        count: count,
        editElement: formElements.singleValueElement(undefined)
      }));
      formElements.bindEvents(container.find('.mass-update-modal .form-elements-container'), {
        setValue: setValue,
        removeValue: removeValue
      });
    };

    var _renderDialog = function() {
      var container = $('.container').append(_.template(confirmDiv)({ count: count,  editElement: '' }));
      var selectedMulti = _.clone(selectedLinearAsset);
      selectedMulti.setValue =  _setValue;
      selectedMulti.removeValue = removeValue;
      container.find('.form-elements-container').html(formElements.renderForm(selectedMulti).find('.editable'));
    };


    var bindEvents = function() {
      $('.mass-update-modal .close').on('click', function() {
        purge();
        onCancel();
      });

      $('.mass-update-modal .save').on('click', function() {
        purge();
        onSave(currentValue);
      });
    };

    var show = function() {
      purge();
      if(assetTypeConfiguration.formElements.singleValueElement)
        renderDialog();
      else
        _renderDialog();
      bindEvents();
    };

    var purge = function() {
      $('.mass-update-modal').remove();
    };

    show();
  }
})(this);
