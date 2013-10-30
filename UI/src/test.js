(function(skillet) {
  //Private Property
  var isHot = true;

  //Public Property
  skillet.ingredient = "Bacon Strips";

  //Public Method
  skillet.fry = function() {
    var oliveOil;

    addItem( "\t\n Butter \n\t" );
    addItem( oliveOil );
    console.log( "Frying " + skillet.ingredient );
  };

  //Private Method
  function addItem( item ) {
    if ( item !== undefined ) {
      console.log( "Adding " + $.trim(item) );
    }
  }
}(this));