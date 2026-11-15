// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.domain

import java.text.Normalizer

/**
 * Secciones del supermercado, en el orden habitual de recorrido.
 * Las palabras clave están normalizadas (minúsculas, sin tildes) en español, inglés,
 * francés, alemán e italiano. Las que llevan espacio son expresiones completas.
 */
enum class ShopCategory(val emoji: String, keywords: String) {
    FRUIT_VEG(
        "🥦",
        """
        fruta frutas verdura verduras hortaliza manzana pera platano banana naranja mandarina limon lima uva fresa fresas frambuesa arandano
        melocoton nectarina albaricoque ciruela cereza kiwi pina mango papaya melon sandia higo granada aguacate tomate lechuga cebolla cebolleta uvas
        ajo ajos patata papa zanahoria pepino calabacin berenjena pimiento brocoli coliflor col coles repollo espinaca acelga puerro apio alcachofa
        esparrago champinon seta judias verdes guisante calabaza boniato remolacha rabano rucula canonigos perejil cilantro albahaca hierbabuena jengibre ensalada
        fruit vegetable vegetables veggies apple pear orange tangerine lemon grape strawberr raspberr blueberr peach plum cherr pineapple
        watermelon avocado tomato lettuce onion garlic potato carrot cucumber zucchini courgette eggplant aubergine broccoli cauliflower cabbage
        spinach leek celery artichoke asparagus mushroom pumpkin beetroot radish arugula rocket parsley coriander basil ginger salad
        legume legumes pomme poire banane clementine citron raisin framboise myrtille peche prune cerise ananas mangue pasteque avocat laitue salade
        oignon ail carotte concombre poivron chou epinard poireau celeri artichaut asperge champignon citrouille potiron betterave radis roquette
        persil coriandre basilic menthe gingembre pomme de terre
        obst gemuse apfel birne zitrone limette traube erdbeere himbeere heidelbeere pfirsich pflaume kirsche melone tomate salat zwiebel knoblauch
        kartoffel karotte mohre gurke paprika brokkoli blumenkohl kohl spinat lauch sellerie spargel pilz pilze kurbis rote bete radieschen rucola
        petersilie basilikum minze ingwer
        frutta verdura mela arancia mandarino limone fragola lampone mirtillo pesca prugna ciliegia anguria pomodoro lattuga insalata cipolla aglio
        carota cetriolo zucchina zucchine melanzana peperone broccolo cavolfiore cavolo spinaci porro sedano carciofo asparago fungo funghi zucca
        barbabietola ravanello prezzemolo basilico zenzero
        """
    ),
    BAKERY(
        "🥖",
        """
        pan panes barra baguette hogaza bollo bolleria croissant cruasan magdalena magdalenas picos pan de molde pan integral empanada
        bread loaf bun buns roll rolls bagel muffin tortilla tortillas pita
        pain brioche viennoiserie
        brot brotchen semmel brezel toast toastbrot
        pane panino panini focaccia cornetto cornetti grissini piadina
        """
    ),
    MEAT(
        "🥩",
        """
        carne pollo pechuga muslo muslos alitas ternera cerdo lomo solomillo costilla costillas chuleta chuletas filete filetes cordero pavo
        hamburguesa hamburguesas albondiga albondigas carne picada conejo
        meat chicken beef pork steak mince minced lamb turkey burger burgers ribs
        viande poulet boeuf porc agneau dinde viande hachee cotelette escalope
        fleisch hahnchen huhn rind rindfleisch schwein schweinefleisch hackfleisch lamm pute schnitzel
        petto manzo maiale bistecca macinato agnello tacchino cotoletta
        """
    ),
    FISH(
        "🐟",
        """
        pescado merluza salmon atun bacalao dorada lubina sardina sardinas boqueron boquerones gamba gambas langostino langostinos mejillon mejillones
        almeja almejas calamar calamares pulpo sepia marisco rape trucha anchoa anchoas
        fish tuna cod hake prawn prawns shrimp mussel mussels clam clams squid octopus seafood trout sardine anchovy anchovies
        poisson saumon thon cabillaud colin crevette crevettes moule moules palourde calamar poulpe fruits de mer truite anchois
        fisch lachs thunfisch kabeljau dorsch garnele garnelen muschel muscheln tintenfisch meeresfruchte forelle sardellen
        pesce salmone tonno merluzzo baccala gambero gamberi gamberetti cozze vongole calamari polpo frutti di mare trota acciughe
        """
    ),
    DELI(
        "🧀",
        """
        jamon chorizo salchichon fuet mortadela queso quesos embutido embutidos salami sobrasada fiambre salchicha salchichas
        ham salami cheese prosciutto pepperoni cheddar mozzarella parmesan bacon sausage sausages
        jambon saucisson fromage comte emmental lardons saucisse
        schinken kase wurst aufschnitt gouda speck wurstchen bratwurst
        prosciutto salame formaggio mortadella bresaola parmigiano grana pecorino salsiccia
        """
    ),
    DAIRY(
        "🥛",
        """
        leche yogur yogures yogurt nata mantequilla huevo huevos kefir cuajada natillas flan margarina requeson leche de avena bebida de avena
        milk yogurt yoghurt cream butter egg eggs margarine oat milk
        lait yaourt yaourts creme beurre oeuf oeufs
        milch joghurt sahne butter ei eier quark
        latte yogurt panna burro uovo uova
        """
    ),
    PANTRY(
        "🥫",
        """
        arroz pasta macarrones espaguetis fideos legumbres lentejas garbanzos alubias harina azucar sal aceite aceite de oliva vinagre conserva conservas
        tomate frito caldo especias pimienta oregano comino pimenton levadura salsa mayonesa ketchup mostaza aceitunas pan rallado quinoa cuscus
        rice spaghetti noodles lentils chickpeas beans flour sugar salt oil olive oil vinegar canned stock broth spices oregano yeast sauce
        mayo mayonnaise mustard olives couscous peanut butter
        riz pates nouilles lentilles pois chiches haricots farine sucre sel huile vinaigre conserve bouillon epices poivre levure sauce
        moutarde olives semoule
        reis nudeln linsen kichererbsen bohnen mehl zucker salz ol essig konserve bruhe gewurz pfeffer hefe sosse senf oliven
        riso penne fusilli lenticchie ceci fagioli farina zucchero sale olio aceto brodo spezie pepe lievito sugo maionese senape passata
        """
    ),
    BREAKFAST(
        "🍪",
        """
        cafe cacao colacao galleta galletas cereales mermelada chocolate miel bizcocho dulces caramelos chuches infusion te manzanilla
        patatas fritas frutos secos almendras nueces cacahuetes pipas palomitas snack snacks
        coffee cocoa biscuits cookie cookies cereal jam honey cake candy sweets tea chips crisps nuts almonds peanuts popcorn
        biscuits gateau cereales confiture chocolat bonbons the tisane noix amandes cacahuetes
        kaffee kakao kekse keks musli cornflakes marmelade konfiture schokolade honig kuchen bonbons sussigkeiten tee chips nusse mandeln erdnusse
        caffe biscotti cereali marmellata confettura cioccolato miele torta caramelle tisana patatine noci mandorle arachidi
        """
    ),
    DRINKS(
        "🥤",
        """
        agua refresco refrescos cola coca zumo zumos cerveza cervezas vino cava gaseosa tonica bebida bebidas isotonica licor ron ginebra whisky vermut sidra
        zumo de naranja
        water soda coke juice beer wine cider drink drinks lemonade tonic rum gin vodka orange juice
        eau jus biere vin cidre boisson boissons limonade rhum jus d orange
        wasser sprudel saft bier wein sekt limo limonade getranke schorle apfelschorle orangensaft
        acqua bibita bibite succo birra vino spumante prosecco aranciata gassosa
        """
    ),
    FROZEN(
        "🧊",
        """
        congelado congelados congelada congeladas helado helados hielo pizza pizzas croquetas
        frozen ice cream ice pizza fish fingers
        surgele surgeles glace glacons
        tiefkuhl tiefgekuhlt eis eiswurfel tiefkuhlpizza
        surgelato surgelati gelato ghiaccio
        """
    ),
    CLEANING(
        "🧽",
        """
        detergente suavizante lejia lavavajillas friegasuelos limpiador estropajo bayeta bayetas bolsas de basura papel de cocina servilletas
        papel de aluminio papel film pilas bombilla amoniaco lavaplatos
        detergent softener bleach dishwasher washing up liquid cleaner sponge bin bags trash bags paper towels napkins foil batteries lightbulb
        lessive adoucissant javel liquide vaisselle nettoyant eponge sacs poubelle essuie tout serviettes papier aluminium piles ampoule
        waschmittel weichspuler spulmittel reiniger schwamm mullbeutel kuchenrolle servietten alufolie batterien glubbirne
        detersivo ammorbidente candeggina varechina sgrassatore spugna sacchetti spazzatura scottex tovaglioli alluminio pile lampadina
        """
    ),
    HYGIENE(
        "🧴",
        """
        champu gel jabon desodorante pasta de dientes dentifrico cepillo de dientes papel higienico compresas tampones panales toallitas crema
        maquinilla cuchillas colonia algodon tiritas ibuprofeno paracetamol medicamentos protector solar
        shampoo conditioner shower gel soap deodorant toothpaste toothbrush toilet paper tampons diapers nappies wipes razor razors plasters sunscreen
        shampooing gel douche savon deodorant dentifrice brosse a dents papier toilette tampons couches lingettes rasoir pansements
        duschgel seife deo zahnpasta zahnburste toilettenpapier klopapier binden windeln feuchttucher rasierer pflaster sonnencreme
        bagnoschiuma sapone deodorante dentifricio spazzolino carta igienica assorbenti tamponi pannolini salviette rasoio cerotti
        """
    ),
    PETS(
        "🐾",
        """
        pienso mascota perro perros gato gatos arena para gatos
        pet dog dogs cat cats kibble cat litter
        croquettes chien chat litiere
        hundefutter katzenfutter tierfutter katzenstreu hund katze
        crocchette cane gatto lettiera
        """
    ),
    OTHER("🛒", "");

    internal val words: Set<String>
    internal val phrases: List<String> = categoryPhrases(name)

    init {
        // Las expresiones de varias palabras se buscan enteras: se quitan del texto para que sus
        // palabras sueltas no cuenten ("patatas fritas" no debe convertir "patatas" en snack).
        var text = " " + keywords.replace(Regex("""\s+"""), " ") + " "
        phrases.forEach { text = text.replace(" $it ", " ") }
        words = text.split(' ').filter { it.isNotBlank() && it !in STOPWORDS }.toSet()
    }
}

private val STOPWORDS = setOf("de", "d", "di", "a", "la", "le", "du", "des", "of", "para", "per", "the", "en", "y", "and")

/** Expresiones de varias palabras que cambian la sección (p. ej. "pasta de dientes" no es despensa). */
private fun categoryPhrases(category: String): List<String> = when (category) {
    "FRUIT_VEG" -> listOf("pomme de terre", "judias verdes", "rote bete")
    "BAKERY" -> listOf("pan de molde", "pan integral")
    "MEAT" -> listOf("carne picada", "viande hachee")
    "FISH" -> listOf("fruits de mer", "frutti di mare")
    "DAIRY" -> listOf("leche de avena", "bebida de avena", "oat milk")
    "PANTRY" -> listOf("aceite de oliva", "tomate frito", "pan rallado", "olive oil", "peanut butter", "pois chiches")
    "BREAKFAST" -> listOf("patatas fritas", "frutos secos")
    "DRINKS" -> listOf("zumo de naranja", "orange juice", "jus d orange")
    "FROZEN" -> listOf("ice cream", "fish fingers")
    "CLEANING" -> listOf(
        "bolsas de basura", "papel de cocina", "papel de aluminio", "papel film", "washing up liquid", "bin bags", "trash bags",
        "paper towels", "liquide vaisselle", "sacs poubelle", "essuie tout"
    )
    "HYGIENE" -> listOf(
        "pasta de dientes", "cepillo de dientes", "papel higienico", "protector solar", "shower gel", "toilet paper",
        "gel douche", "brosse a dents", "papier toilette", "carta igienica"
    )
    "PETS" -> listOf("arena para gatos", "cat litter")
    else -> emptyList()
}

/**
 * Clasifica productos de la compra en secciones.
 *
 * - Primero se consulta lo aprendido ([overrides]: lo que el usuario movió a mano).
 * - Una expresión completa ("pasta de dientes") gana a una palabra suelta ("pasta").
 * - "congelado", "frozen", "surgelé"… mandan el producto a Congelados.
 * - Si no, gana la primera palabra reconocida ("helado de chocolate" → Congelados), admitiendo
 *   plurales ("tomates", "strawberries") y compuestos alemanes ("Vollmilch" → Milch).
 */
object ShopCategorizer {

    private val UNITS = setOf(
        "kg", "g", "gr", "grs", "l", "ml", "cl", "lt", "x", "ud", "uds", "unidad", "unidades", "paquete", "paquetes", "bote", "botes",
        "lata", "latas", "botella", "botellas", "pack", "docena", "bolsa", "caja", "cajas", "pcs", "stk", "stuck", "pz", "bottle", "can", "cans"
    )
    private val FROZEN_MARKERS = listOf("congelad", "frozen", "surgel", "tiefgekuhl", "tiefkuhl")

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase().replace("ß", "ss").replace("œ", "oe").replace("æ", "ae"), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()

    /** Clave con la que se recuerda una sección elegida a mano (sin cantidades ni unidades). */
    fun termFor(title: String): String =
        normalize(title).split(' ').filter { it.isNotEmpty() && it.none(Char::isDigit) && it !in UNITS }.joinToString(" ")

    fun categorize(title: String, overrides: Map<String, ShopCategory> = emptyMap()): ShopCategory {
        val term = termFor(title)
        if (term.isEmpty()) return ShopCategory.OTHER
        overrides[term]?.let { return it }

        val tokens = term.split(' ')
        if (tokens.any { t -> FROZEN_MARKERS.any { t.startsWith(it) } }) return ShopCategory.FROZEN

        val padded = " $term "
        ShopCategory.entries.firstOrNull { cat -> cat.phrases.any { padded.contains(" $it ") } }?.let { return it }

        // Coincidencia exacta de palabra (gana sobre plurales/compuestos) y, a igualdad, la primera palabra.
        var best: ShopCategory? = null
        var bestScore = 0
        var bestIndex = Int.MAX_VALUE
        tokens.forEachIndexed { index, token ->
            for (cat in ShopCategory.entries) {
                val score = score(token, cat.words)
                if (score > bestScore || (score == bestScore && score > 0 && index < bestIndex)) {
                    best = cat
                    bestScore = score
                    bestIndex = index
                }
            }
        }
        return best ?: ShopCategory.OTHER
    }

    private fun score(token: String, words: Set<String>): Int {
        if (token in words) return 3
        for (word in words) {
            if (word.length < 4) continue
            // Plurales y variantes: "tomates", "strawberries" (stem "strawberr"), "naranjas".
            if (token.startsWith(word) && token.length - word.length <= 3) return 2
            // Plural italiano y género: "pomodori"/"pomodoro", "patate"/"patata", "pere"/"pera".
            if (token.length == word.length && token.dropLast(1) == word.dropLast(1)) return 2
            // Compuestos alemanes: "vollmilch", "bergkase", "orangensaft".
            if (word.length >= 4 && token.length > word.length + 2 && token.endsWith(word)) return 1
        }
        return 0
    }

    /** ¿Parece una lista de la compra? (para activar las secciones al crearla) */
    fun isShoppingListName(name: String): Boolean {
        val n = normalize(name)
        return SHOPPING_NAMES.any { n.contains(it) }
    }

    private val SHOPPING_NAMES = listOf(
        "compra", "super", "mercado", "shopping", "grocer", "courses", "epicerie", "einkauf", "spesa", "despensa", "lidl", "mercadona", "aldi"
    )
}
