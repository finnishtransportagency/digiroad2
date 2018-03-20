(function (root) {
  root.LinkPropertyMassUpdateDialog = {
    show: init
  };

  function init(options) {
    var linkCount = options.linkCount,
        onCancel = options.onCancel,
        onSave = options.onSave;

    var functionalClasses = [
      {text: '1', value: 1},
      {text: '2', value: 2},
      {text: '3', value: 3},
      {text: '4', value: 4},
      {text: '5', value: 5},
      {text: '6', value: 6},
      {text: '7', value: 7},
      {text: '8', value: 8}
    ];

    var linkTypes = [
      {value: 1, text: 'Moottoritie'},
      {value: 2, text: 'Moniajoratainen tie'},
      {value: 3, text: 'Yksiajoratainen tie'},
      {value: 4, text: 'Moottoriliikennetie'},
      {value: 5, text: 'Kiertoliittymä'},
      {value: 6, text: 'Ramppi'},
      {value: 7, text: 'Levähdysalue'},
      {value: 8, text: 'Kevyen liikenteen väylä'},
      {value: 9, text: 'Jalankulkualue'},
      {value: 10, text: 'Huolto- tai pelastustie'},
      {value: 11, text: 'Liitännäisliikennealue'},
      {value: 12, text: 'Ajopolku'},
      {value: 13, text: 'Huoltoaukko moottoritiellä'},
      {value: 21, text: 'Lautta/lossi'}
    ];

    var createOptionElements = function(options) {
      return ['<option value=""></option>'].concat(_.map(options, function(option) {
        return '<option value="' + option.value + '">' + option.text + '</option>';
      })).join('');
    };

    var confirmDiv =
      _.template(
        '<div class="modal-overlay mass-update-modal">' +
        '<div class="modal-dialog">' +
        '<div class="content">' +
        'Olet valinnut <%- linkCount %> tielinkkiä' +
        '</div>' +
        '<div class="form-group">Tyhjäksi jätetyn kentän arvo ei muutu</div>' +
        '<div class="form-group editable">' +
        '<label class="control-label">Toiminnallinen luokka</label>' +
        '<select id="functional-class-selection" class="form-control">' + createOptionElements(functionalClasses) + '</select>' +
        '</div>' +

        '<div class="form-group editable">' +
        '<label class="control-label">Tielinkin tyyppi</label>' +
        '<select id="link-type-selection" class="form-control">' + createOptionElements(linkTypes) + '</select>' +
        '</div>' +

        '<div class="actions">' +
        '<button class="btn btn-primary save">Tallenna</button>' +
        '<button class="btn btn-secondary close">Peruuta</button>' +
        '</div>' +
        '</div>' +
        '</div>');

    var renderDialog = function() {
      $('.container').append(confirmDiv({
        linkCount: linkCount
      }));
    };

    var bindEvents = function() {
      $('.mass-update-modal .close').on('click', function() {
        purge();
        onCancel();
      });
      $('.mass-update-modal .save').on('click', function() {
        var modal = $('.modal-dialog');
        modal.find('.actions button').attr('disabled', true);

        var functionalClassSelection = $('#functional-class-selection').val();
        var functionalClass = _.isEmpty(functionalClassSelection) ? undefined : parseInt(functionalClassSelection, 10);

        var linkTypeSelection = $('#link-type-selection').val();
        var linkType = _.isEmpty(linkTypeSelection) ? undefined : parseInt(linkTypeSelection, 10);

        purge();

        onSave(functionalClass, linkType);
      });
    };

    var show = function() {
      renderDialog();
      bindEvents();
    };

    var purge = function() {
      $('.mass-update-modal').remove();
    };

    show();
  }
})(this);
