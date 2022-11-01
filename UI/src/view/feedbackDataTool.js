(function (root) {
  root.FeedbackDataTool = function (feedbackCollection, layerName, authorizationPolicy, eventCategory) {
    var me = this;
    me.collection = feedbackCollection;
    me.layerName = layerName;
    me.authorizationPolicy = authorizationPolicy;
    me.eventCategory = eventCategory;
    var MAX_CHARACTER_LENGTH = 3747;
    function events() {
      return _.map(arguments, function(argument) { return me.eventCategory + ':' + argument; }).join(' ');
    }

    var allowFeedBack = function () {
      return _.includes(['manoeuvre', 'linkProperty'], me.layerName) || applicationModel.getSelectedTool() === 'Select';
    };

    var renderFeedbackLink = function (enable) {
      var infoContent = $('.feedback-data');
      if (enable && allowFeedBack() ) {
        infoContent.html('<button id="feedback-data" class="feedback-data-link btn btn-quaternary" onclick=location.href="javascript:void(0)" >Anna palautetta kohteesta</button>');
      } else {
        infoContent.find('#feedback-data').remove();
      }

      $('#feedback-data').on('click', function(){
        open();
      });
    };

    var open = function(){
      if(applicationModel.getSelectedLayer() === me.layerName) {
        var selectedData = getData();
        renderDialog(selectedData, me.layerName);
        bindEvents(selectedData);
        applicationModel.setApplicationkState(applicationState.Feedback);
      }
    };

    this.closeAssetFeedBack = function() {
      purge();
      $('.feedback-data').find('#feedbackData').remove();
      applicationModel.setApplicationkState(applicationState.Normal);
    };

    this.closeFeedback = function(){
      purge();
      renderFeedbackLink(false);
      applicationModel.setApplicationkState(applicationState.Normal);
    };

    this.initFeedback = function(){
      if (applicationModel.getSelectedLayer() === me.layerName)
        renderFeedbackLink(true);
    };

    var applicationListeners = function(){
      eventbus.on("feedback:send", function() {
        removeSpinner();
        new GenericConfirmPopup("Kiitos palautteesta", {type: 'alert'});
        purge();
        applicationModel.setApplicationkState(applicationState.Normal);
      });

      eventbus.on("feedback:failed",function() {
        removeSpinner();
        new GenericConfirmPopup("Palautteen lähetyksessä esiintyi virhe. Yritys toistuu automaattisesti hetken päästä.", {type: 'alert'});
      });

      eventbus.on('linkProperties:unselected manoeuvres:unselected speedLimit:unselect asset:closed', me.closeFeedback);

      eventbus.on('closeFeedBackData', me.closeAssetFeedBack);

      eventbus.on('linkProperties:selected linkProperties:cancelled manoeuvres:selectedAvailable speedLimit:selected speedLimit:cancelled asset:modified manoeuvres:selected', me.initFeedback);

      eventbus.on(events('selected', 'cancelled'), me.initFeedback);

      eventbus.on(events('unselect'), me.closeFeedback);

      eventbus.on(me.layerName + ':unselected', me.closeFeedback);

      eventbus.on(me.layerName + ':selected ' + me.layerName + ':cancelled' ,me.initFeedback);

      eventbus.on("feedback:tooBig",function() {
        removeSpinner();
        new GenericConfirmPopup("Palaute oli liian pitkä. Maksimi merkki määrä on 3747", {type: 'alert'});
      });
    };

    $(document).ready(function () {
      $.validator.addMethod("pattern", function (phone_number, element, pattern) {
        return this.optional(element) || phone_number.match(new RegExp(pattern));
      }, "Invalid phone number \"xxx xx xx xxx\" or \"+xxx xxx xxx xxx\"");
    });

    var bindEvents = function (selectedData) {
      $('.feedback-modal .save').on('click', function() {
        applicationModel.setApplicationkState('normal');
        var formElements = $(this).closest('.modal-dialog').find('.form-horizontal');
        var values = formElements.serializeArray();
        values.push(
          {name: 'linkId',    value:  selectedData.linkId},
          {name: 'assetId',   value : selectedData.assetId },
          {name: 'assetName', value : selectedData.title},
          {name: 'typeId',    value : selectedData.typeId},
          {name: 'freeText',  value: $('#freeTextData').html()});

        var text = $('.feedback-message-asset')[0].innerText;
        if (formElements.valid()) {
          addSpinner();
          if (text.length<=MAX_CHARACTER_LENGTH){
            me.collection.sendFeedbackData(values);
          }else{
            eventbus.trigger("feedback:tooBig");
          }
        }
      });

      $(".feedback-message-asset").on("change keyup paste",function() {
        var message = $('.feedback-message-asset')[0].innerText;
        $(".feedback-message-count").text(message.length+"/"+MAX_CHARACTER_LENGTH);
      });
      
      $('#phoneNumber').keyup(function() {
        $(this).valid();
      });

      $(' .feedback-modal .sulje, .feedback-modal .cancel').on('click', function() {
        applicationModel.setApplicationkState('normal');
        purge();
      });
    };

    var addSpinner = function () {
      $('.container').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
    };

    var removeSpinner = function(){
      $('.spinner-overlay').remove();
    };

    var purge = function() {
      $('.feedback-modal').empty();
    };

    var renderDialog = function(selectedAsset, layer) {
      var dialog = createFeedbackForm(selectedAsset, layer);
      $('#feedbackData').html(dialog);
      $(".feedback-message-count").text(0+"/"+MAX_CHARACTER_LENGTH);
    };

    var getData = function(){
      return me.collection.get();
    };

    var setDropdownValue = function(layer, dialog){
      if(layer === 'linkProperty')
        dialog.find('#feedbackDataType').val('Geometriapalaute');
      else
        dialog.find('#feedbackDataType').val('Aineistopalaute');
    };

    var userInformationEditableFields = function(){
      return $(
        '<div class="form-element">' +
        '<label class="control-label">Nimi</label>' +
        '<input type="text" name="name" class="form-control">' +
        '</div>' +
        '<div class="form-element">' +
        '<label class="control-label">Sähköposti</label>' +
        '<input type="email" name="email" class="form-control">' +
        '</div>' +
        '<div class="form-element">' +
        '<label class="control-label">Puhelinnumero</label>' +
        '<input type="input" name="phoneNumber" pattern="^([\\d\\s]{3}([ -]?[\\d\\s]{2}){2}[ -]?[\\d\\s]{3}|([\\d\\s]{4}([ -]?[\\d\\s]{3}){2})|[+]\\d{3}([ -]?[\\d\\s]{3}){3})$" id="phoneNumber" class="form-control">' +
        '</div>');
    };

    me.message = function(){
      return 'Anna palautetta kohteesta';
    };

    me.formContent = function (selectedAsset) {
      var idName = me.layerName === "massTransitStop" ? 'Valtakunnallinen id' : 'Kohteen id';

      return '<div class="form-element">' +
        '<label class="control-label">Linkin id</label>' +
        '<span id="linkId" >'+ selectedAsset.linkId.join(', ') +'</span>'+
        '</div>' +
        '<div class="form-element">' +
        '<label class="control-label">' + idName + '</label>' +
        '<span id="assetId" >'+ selectedAsset.assetId.join(', ')+'</span>'+
        '</div>' +
        '<div class="form-element">' +
        '<label class="control-label">Tietolaji</label>' +
        '<span id="assetName">'+selectedAsset.title+'</span>'+
        '</div>' +
        '<div class="form-element">' +
        '<label class="control-label" id="feedbackType">Palautteen tyyppi</label>' +
        '<select  name="feedbackDataType" id ="feedbackDataType" class="form-control">'+
        '<option value="Geometriapalaute">Geometriapalaute </option>'+
        '<option value="Aineistopalaute">Aineistopalaute</option>'+
        '</select>' +
        '</div>' +
        '<div class="form-element">' +
        '<label class="control-label">Palaute</label>' +
        '<div contenteditable="true" id="freeTextData" class="form-control feedback-message-asset"></div>'+
          '<label class="control-label">Merkkien määrä:</label>' +
          '<label id="feedback-message-count" class="feedback-message-count"></label>' +
        '</div>' +
        '<div class="form-element">' +
        '<label class="control-label">K-tunnus</label>' +
        '<span id="kidentifier">'+me.authorizationPolicy.username+'</span>'+
        '</div>';
    };

    me.buttons = function () {
      return '<button class = "btn btn-primary save">Lähetä</button>' +
        '<button class = "btn btn-secondary cancel">Peruuta</button>';
    };

    var createFeedbackForm = function(selectedAsset, layer) {
      var  dialog =  $('<div class="modal-dialog">' +
        '<div class="content">' + me.message() + '<a class="header-link sulje">Sulje</a></div>' +
        '<form class="form form-horizontal" role="form">' +
        '<div class="form-group" id="feedbackForm">' +
        me.formContent(selectedAsset) +
        '</div>' +
        '</form>' +
        '<div class="actions">' +
        me.buttons() +
        '</div>' +
        '</div>' +
        '</div>');

      setDropdownValue(layer, dialog);
      dialog.find("#feedbackForm").append(userInformationEditableFields());
      return dialog;
    };

    applicationListeners();
  };
})(this);